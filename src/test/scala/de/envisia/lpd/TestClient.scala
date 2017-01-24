package de.envisia.lpd

import java.nio.file.{ Files, Paths }

import akka.actor.ActorSystem
import akka.stream.scaladsl.FileIO
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }

import scala.concurrent.Await
import scala.concurrent.duration._

object TestClient {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val ec = system.dispatcher
    implicit val mat = ActorMaterializer(ActorMaterializerSettings(system))
    val path = Paths.get("/Users/schmitch/sto/tmp/transformed_3290018523_WLT_WE_0456_cf572fb2.ps")
    Await.result(new LpdClient().print("192.168.1.37", "schmitch", "print", path, "transformed_3290018523_WLT_WE_0456_cf572fb2.ps").recover {
      case e: Exception => e.printStackTrace(); Seq()
    }, 10.minutes)
    Await.result(system.terminate(), 10.minutes)
  }

}