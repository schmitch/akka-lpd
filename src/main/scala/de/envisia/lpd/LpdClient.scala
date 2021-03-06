package de.envisia.lpd

import java.net.InetSocketAddress
import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source, Tcp }
import akka.util.ByteString

import scala.concurrent.Future

class LpdClient(hostname: String = "akka")(implicit system: ActorSystem, mat: Materializer) {

  import LpdProtocol._

  private def flow(host: String, port: Int = 515) = {
    val remote = InetSocketAddress.createUnresolved(host, port)
    // Currently a LPD Client normally uses a well known port for recieving data
    // val local = None // Some(new InetSocketAddress(1025))
    Tcp().outgoingConnection(remote, None)
  }

  def shortQueue(host: String, port: Int, queue: String, additional: String): Future[String] = {
    Source
      .single(createExtendedCommand(3.toByte, queue, additional))
      .via(flow(host, port))
      .runFold("")((s, bs) => s + bs.utf8String)
  }

  def shortQueue(host: String, queue: String, additional: String): Future[String] = {
    this.shortQueue(host, 515, queue, additional)
  }

  def queue(host: String, port: Int, queue: String, additional: String): Future[String] = {
    Source
      .single(createExtendedCommand(4, queue, additional))
      .via(flow(host, port))
      .runFold("")((s, bs) => s + bs.utf8String)
  }

  def queue(host: String, queue: String, additional: String): Future[String] = {
    this.queue(host, 515, queue, additional)
  }

  def removeJobs(host: String, queue: String, agent: String): Future[Done] = {
    removeJobs(host, 515, queue, agent)
  }

  def removeJobs(host: String, port: Int, queue: String, agent: String): Future[Done] = {
    Source
      .single(createExtendedCommand(5, queue, agent))
      .via(flow(host, port))
      .runWith(Sink.ignore)
  }

  def print(host: String, username: String, queue: String, path: Path, filename: String): Future[Done] = {
    print(host, 515, username, queue, path, filename)
  }

  def print(host: String, port: Int, username: String, queue: String, path: Path, filename: String): Future[Done] = {
    print(host, port, username, queue, FileIO.fromPath(path, chunkSize = 4096), Files.size(path), filename)
  }

  def print(
    host: String,
    username: String,
    queue: String,
    source: Source[ByteString, _],
    size: Long,
    filename: String
  ): Future[Done] = {
    print(host, 515, username, queue, source, size, filename)
  }

  def print(
    host: String,
    port: Int,
    username: String,
    queue: String,
    source: Source[ByteString, _],
    size: Long,
    filename: String
  ): Future[Done] = {
    val connectionFlow: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] = {
      flow(host, port).join(new LpdProtocol(size, username, queue, hostname, filename))
    }

    source
      .via(connectionFlow)
      .map(_.utf8String)
      .toMat(Sink.ignore)(Keep.right)
      .run()
  }

}
