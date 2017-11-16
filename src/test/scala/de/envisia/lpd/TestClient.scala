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
    val path = Paths.get("/Users/schmitch/z_important/TestKRONEN.pdf")
    val client = new LpdClient()
    val uuid = s"JOB2"
    val ip = "192.168.179.3"

    //    val ret = Await.result(
    //      client
    //        .print(ip, uuid, "print", path, "TestKRONEN.pdf")
    //        .recover {
    //          case e: Exception => e.printStackTrace(); Done
    //        },
    //      10.minutes)

    // println(s"Ret")
    println(s"UUID: $uuid")
    for { _ <- 1 to 10000000 } yield {
      val snmpClient = new SnmpStatusClient(ip)
      snmpClient.pollStatus(uuid) match {
        case Success(reason) => println(s"JobReason: $reason")
        case Failure(t) => t.printStackTrace()
      }
    }

    Thread.sleep(10000)

    Await.result(system.terminate(), 10.minutes)

  }

}
