package de.envisia.lpd

import java.util.concurrent.TimeUnit

import akka.stream.Materializer
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.{ GenericAddress, OID, OctetString, VariableBinding }
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.{ CommunityTarget, PDU, Snmp }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

class SnmpStatusClient(ip: String)(implicit materializer: Materializer) {
  private[this] def transport = new DefaultUdpTransportMapping
  private[this] def snmpClient = {
    val inner = new Snmp(transport)
    transport.listen()
    (inner, transport)
  }
  private[this] def snmpTarget = {
    val targetAddress = GenericAddress.parse(s"udp:$ip/161")
    val target = new CommunityTarget
    target.setCommunity(new OctetString("public"))
    target.setAddress(targetAddress)
    target.setRetries(2)
    target.setTimeout(1500)
    target.setVersion(SnmpConstants.version2c)
    target
  }

  private def request(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      oid: OID,
      typ: Int,
  ): Try[VariableBinding] = {
    val pdu = new PDU
    pdu.add(new VariableBinding(oid))
    pdu.setType(typ)
    snmp.listen()
    val event = snmp.send(pdu, target, null)
    val response = event.getResponse
    Try(response.get(0))
  }

  private def nameOid(name: String) = {
    new OID("1.3.6.1.4.1.2699.1.1.1.2.1.1.3.49.48").append(new OctetString(name).toSubIndex(true))
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
      name: String): Try[Int] = {
    @tailrec
    def call(current: OID): Try[Int] = {
      request(snmp, target, transportMapping, current, PDU.GETNEXT) match {
        case Success(ret) =>
          val oid = ret.getOid
          // we need to check if oid startsWith the queried oid, else we have bogus values
          if (oid.startsWith(current)) {
            Success(ret.getVariable.toInt)
          } else {
            TimeUnit.MILLISECONDS.sleep(250)
            call(current)
          }
        case Failure(t) => Failure(t)
      }
    }

    call(nameOid(name))
  }

  def status(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      jobIndex: Int): Try[JobState] = {
    Try(
      request(snmp, target, transportMapping, statusOid(jobIndex), PDU.GET)
          .map(status => JobState.fromInt(status.getVariable.toInt))
          .get)
  }

  def reason(
      snmp: Snmp,
      target: CommunityTarget,
      transportMapping: DefaultUdpTransportMapping,
      jobIndex: Int): Try[JobReason] = {
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
    state match {
      case JobState.Unknown | JobState.Completed | JobState.Aborted | JobState.Canceled => Success(())
      case _ =>
        TimeUnit.MILLISECONDS.sleep(750)
        status(snmp, target, transportMapping, index) match {
          case Success(newState) => poll(snmp, target, transportMapping, index, newState)
          case Failure(t) => Failure(t)
        }
    }
  }

  def pollStatus(name: String): Try[JobReason] = {
    Try {
      val (snmp, transport) = snmpClient
      val target = snmpTarget
      (snmp, transport, target)
    }.flatMap {
      case (snmp, transport, target) =>
        val jobReason = (for {
          index <- findJobIndex(snmp, target, transport, name)
          state <- status(snmp, target, transport, index)
        } yield (index, state)).flatMap {
          case (index, state) =>
            poll(snmp, target, transport, index, state).map(_ => index)
        }.flatMap { index =>
          reason(snmp, target, transport, index)
        }
        snmp.close()
        transport.close()
        jobReason
    }
  }

}
