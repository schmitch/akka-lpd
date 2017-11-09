package de.envisia.lpd

import java.net.InetSocketAddress
import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source, Tcp }
import akka.util.ByteString

import scala.concurrent.Future

class LpdClient(hostname: String = "akka")(implicit system: ActorSystem, mat: Materializer) {

  import LpdProtocol._

  private def flow(host: String, port: Int = 515) = {
    val remote = InetSocketAddress.createUnresolved(host, port)
    // val local = None // Some(new InetSocketAddress(1025))
    Tcp().outgoingConnection(remote, None)
  }

  private val atomicInt = new AtomicInteger(1)

  private def getJobId: Int = {
    // creates a 3 digit job id
    atomicInt.updateAndGet(x => if (x + 1 == 999) 1 else x + 1)
  }

  def queue(host: String, port: Int, queue: String): Future[String] = {
    Source.single(createBaseCommand(4, queue)).via(flow(host, port)).runFold("")((s, bs) => s + bs.utf8String)
  }

  def queue(host: String, queue: String): Future[String] = {
    this.queue(host, 515, queue)
  }

  def print(host: String, username: String, queue: String, path: Path, filename: String): Future[Seq[String]] = {
    print(host, 515, username, queue, path, filename)
  }

  def print(host: String, port: Int, username: String, queue: String, path: Path, filename: String): Future[Seq[String]] = {
    print(host, port, username, queue, FileIO.fromPath(path, chunkSize = 4096), Files.size(path), filename)
  }

  def print(host: String, username: String, queue: String, source: Source[ByteString, _], size: Long, filename: String): Future[Seq[String]] = {
    print(host, 515, username, queue, source, size, filename)
  }

  def print(host: String, port: Int, username: String, queue: String, source: Source[ByteString, _], size: Long, filename: String): Future[Seq[String]] = {
    val connectionFlow: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] = {
      flow(host, port).join(new LpdProtocol(size, username, queue, getJobId, hostname, filename))
    }

    source.via(connectionFlow)
      .map(_.utf8String)
      .toMat(Sink.seq)(Keep.right)
      .run()
  }

}
