package de.envisia.lpd

import java.nio.file.Paths

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object TestClient {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val ec = system.dispatcher
    implicit val mat = ActorMaterializer(ActorMaterializerSettings(system))
    val path = Paths.get("/Users/schmitch/TestKRONEN.pdf")
    val client = new LpdClient()
    val uuid = s"JOB60004"
    val ip = "192.168.33.37"

    val ret = Await.result(
      client
        .print(ip, uuid, "print", path, "ZugferdDevZugferdDevZugferdDevZugferdDevZugferdDevZugferdDev.en.pdf")
        .recover {
          case e: Exception => e.printStackTrace(); Done
        },
      10.minutes)

    // println(s"Ret")
    println(s"UUID: $uuid")
    val snmpClient = new SnmpStatusClient(ip)
    snmpClient.pollStatus(uuid) match {
      case Success(reason) => println(s"JobReason: $reason")
      case Failure(t) => t.printStackTrace()
    }

    Await.result(system.terminate(), 10.minutes)

  }

}
