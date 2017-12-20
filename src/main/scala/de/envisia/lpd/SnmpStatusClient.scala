package de.envisia.lpd

import java.util.concurrent.TimeUnit

import akka.stream.Materializer
import org.slf4j.LoggerFactory
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.{ GenericAddress, OID, OctetString, VariableBinding }
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.{ CommunityTarget, PDU, Snmp }

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

class SnmpStatusClient(ip: String)(implicit materializer: Materializer) {

  import SnmpStatusClient._

  private val logger = LoggerFactory.getLogger("de.envisia.lpd.status")

  private[this] def createTransport() = new DefaultUdpTransportMapping
  private[this] def createSnmpTarget() = {
    val targetAddress = GenericAddress.parse(s"udp:$ip/161")
    val target = new CommunityTarget
    target.setCommunity(new OctetString("public"))
    target.setAddress(targetAddress)
    target.setRetries(2)
    target.setTimeout(1500)
    target.setVersion(SnmpConstants.version2c)
    target
  }
  private[this] def createSnmpClient() = {
    val transport = createTransport()
    val inner = new Snmp(transport)
    transport.listen()
    try {
      val target = createSnmpTarget()
      (inner, transport, target)
    } catch {
      case NonFatal(t) =>
        transport.close()
        throw t
    }
  }

  private def request(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      oid: OID,
      typ: Int,
  ): Try[VariableBinding] = {
    Try {
      val pdu = new PDU
      pdu.add(new VariableBinding(oid))
      pdu.setType(typ)
      val event = snmp.send(pdu, target, null)
      val response = event.getResponse
      response.get(0)
    }
  }

  private def nameOid(name: String) = {
    new OID("1.3.6.1.4.1.2699.1.1.1.2.1.1.3.49.48")
      .append(new OctetString(name).toSubIndex(true))
  }

  private def statusOid(jobIndex: Int) = {
    new OID("1.3.6.1.4.1.2699.1.1.1.3.1.1.2.1").append(jobIndex)
  }

  private def reasonOid(jobIndex: Int) = {
    new OID("1.3.6.1.4.1.2699.1.1.1.3.1.1.3.1").append(jobIndex)
  }

  def findJobIndex(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      name: String,
  ): Try[Int] = {
    @tailrec
    def call(current: OID, currentTries: Int): Try[Int] = {
      request(snmp, target, transportMapping, current, PDU.GETNEXT) match {
        case Success(ret) =>
          val oid = ret.getOid
          // we need to check if oid startsWith the queried oid, else we have bogus values
          if (oid.startsWith(current)) {
            Success(ret.getVariable.toInt)
          } else if (currentTries >= 0) {
            Try(TimeUnit.MILLISECONDS.sleep(1000)) match {
              case Success(_) => call(current, currentTries - 1)
              case Failure(t) => Failure(t)
            }
          } else {
            Failure(JobIndexNotFoundException)
          }
        case Failure(t) => Failure(t)
      }
    }

    call(nameOid(name), 20)
  }

  def status(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      jobIndex: Int
  ): Try[JobState] = {
    Try(
      request(snmp, target, transportMapping, statusOid(jobIndex), PDU.GET)
        .map(status => JobState.fromInt(status.getVariable.toInt))
        .get)
  }

  def reason(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      jobIndex: Int
  ): Try[JobReason] = {
    Try(
      request(snmp, target, transportMapping, reasonOid(jobIndex), PDU.GET)
        .map(status => JobReason.fromInt(status.getVariable.toInt))
        .get)
  }

  @tailrec
  private def poll(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      index: Int,
      state: JobState
  ): Try[Unit] = {
    logger.debug(s"Poll State: $state")
    state match {
      case JobState.Unknown | JobState.Completed | JobState.Aborted | JobState.Canceled =>
        Success(())
      case _ =>
        Try(TimeUnit.MILLISECONDS.sleep(750)) match {
          case Success(_) =>
            status(snmp, target, transportMapping, index) match {
              case Success(newState) =>
                poll(snmp, target, transportMapping, index, newState)
              case Failure(t) => Failure(t)
            }
          case Failure(t) => Failure(t)
        }
    }
  }

  private def findFirstJobReason(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      name: String
  ) = {
    for {
      index <- findJobIndex(snmp, target, transportMapping, name)
      state <- status(snmp, target, transportMapping, index)
    } yield (index, state)
  }

  def pollStatus(name: String): Try[JobReason] = {
    Try(createSnmpClient())
      .flatMap {
        case (snmp, transport, target) =>
          val jobReason = findFirstJobReason(snmp, target, transport, name)
            .flatMap {
              case (index, state) =>
                poll(snmp, target, transport, index, state).map(_ => index)
            }
            .flatMap { index =>
              reason(snmp, target, transport, index)
            }

          snmp.close()
          transport.close()
          jobReason
      }
      .recover {
        case JobIndexNotFoundException => JobReason.Unknown
      }
  }

}

object SnmpStatusClient {

  case object PrintProcessingStoppedException extends Exception
  case object JobIndexNotFoundException extends Exception

}
