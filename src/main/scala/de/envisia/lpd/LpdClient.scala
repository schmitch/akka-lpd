package de.envisia.lpd

import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class LpdClient(host: String, port: Int = 515, hostname: String = "akka")(implicit system: ActorSystem, mat: Materializer) {

  import LpdProtocol._

  private def flow = Tcp().outgoingConnection(host, port)
  @volatile private var jobId: Int = 11

  def queue(queue: String): Future[String] = {
    Source.single(createBaseCommand(4, queue)).via(flow).runFold("")((s, bs) => s + bs.utf8String)
  }

  def print(queue: String, path: Path): Future[Seq[String]] = {
    // sets the 3 digit job id
    if (jobId < 999) {
      jobId += 1
    } else {
      jobId = 12
    }

    val filename = "7440018391_WLT_WE_77330.ps"

    val connectionFlow: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] = {
      flow.join(new LpdProtocol(Files.size(path), queue, jobId, hostname, filename))
    }

    FileIO.fromPath(path, chunkSize = 4096)
        .via(connectionFlow)
        .map(_.utf8String)
        .toMat(Sink.seq)(Keep.right)
        .run()
  }

}
