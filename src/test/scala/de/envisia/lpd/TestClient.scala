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
    implicit val mat = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy({ e =>
      println(e)
      Supervision.Stop
    }))
    val path = Paths.get("/Users/schmitch/sto/tmp/7440018391_WLT_WE_77330.ps")
    val size = Files.size(path)
    Await.result(new LpdClient().print("172.16.206.100", "Cups-PDF", FileIO.fromPath(path, 4096), size, "7440018391_WLT_WE_77330.ps").recover {
      case e: Exception => e.printStackTrace(); Seq()
    }, 10.minutes)
    Await.result(system.terminate(), 10.minutes)
  }

}