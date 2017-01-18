package de.envisia.lpd

import java.nio.file.{Files, Path, Paths}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class LpdClient(hostname: String = "akka")(implicit system: ActorSystem, mat: Materializer) {

  import LpdProtocol._

  private def flow(host: String, port: Int = 515) = Tcp().outgoingConnection(host, port)
  @volatile private var jobId: Int = 11

  def queue(host: String, port: Int, queue: String): Future[String] = {
    Source.single(createBaseCommand(4, queue)).via(flow(host, port)).runFold("")((s, bs) => s + bs.utf8String)
  }

  def queue(host: String, queue: String): Future[String] = {
    this.queue(host, 515, queue)
  }

  def print(host: String, queue: String, path: Path, filename: String): Future[Seq[String]] = {
    print(host, 515, queue, path, filename)
  }

  def print(host: String, port: Int, queue: String, path: Path, filename: String): Future[Seq[String]] = {
    print(host, port, queue, FileIO.fromPath(path, chunkSize = 4096), Files.size(path), filename)
  }

  def print(host: String, port: Int, queue: String, source: Source[ByteString, Any], size: Long, filename: String): Future[Seq[String]] = {
    // sets the 3 digit job id
    if (jobId < 999) {
      jobId += 1
    } else {
      jobId = 12
    }

    val connectionFlow: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] = {
      flow(host, port).join(new LpdProtocol(size, queue, jobId, hostname, filename))
    }

    source.via(connectionFlow)
        .map(_.utf8String)
        .toMat(Sink.seq)(Keep.right)
        .run()
  }

}
