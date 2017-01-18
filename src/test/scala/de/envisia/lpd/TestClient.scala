package de.envisia.lpd

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }

import scala.concurrent.Await
import scala.concurrent.duration._

object TestClient {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy({ e =>
      println(e)
      Supervision.Stop
    }))

    Await.result(new LpdClient().print("172.16.206.100", "Cups-PDF", Paths.get("/Users/schmitch/sto/tmp/7440018391_WLT_WE_77330.ps"), "7440018391_WLT_WE_77330.ps"), 10.minutes)
    Await.result(system.terminate(), 10.minutes)
  }

}