package de.envisia.lpd

import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

private[lpd] object LpdProtocol {

  private val charset = StandardCharsets.UTF_8

  private[lpd] final val LF = '\n'
  private[lpd] final val SP = ' '
  private[lpd] final val EMPTY = ByteString(0)

  private implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

  def createSimpleCommand(ctrl: Byte): ByteString = {
    ByteString.newBuilder.putByte(ctrl).putByte(LF).result()
  }

  def createBaseCommand(ctrl: Byte, queue: String): ByteString = {
    ByteString.newBuilder.putByte(ctrl).putBytes(queue.getBytes(charset)).putByte(LF).result()
  }

  def createCommand(ctrl: Byte, name: String, size: Long): ByteString = {
    ByteString.newBuilder.putByte(ctrl)
        .putBytes(size.toString.getBytes(charset))
        .putByte(SP)
        .putBytes(name.getBytes(charset))
        .putByte(LF)
        .result()
  }

  def buildControlFile(hostname: String, filename: String, printname: String): ByteString = {
    ByteString.newBuilder
        .putBytes(s"H$hostname".getBytes(charset)).putByte(LF) // Hostname
        .putBytes(s"P$hostname".getBytes(charset)).putByte(LF) // User identification (needs to be included)
        .putBytes(s"U$filename".getBytes(charset)).putByte(LF) // File is no longer needed
        .putBytes(s"o$filename".getBytes(charset)).putByte(LF) // File sent as either f: Formatted or o: PostScript
        .putBytes(s"N$printname".getBytes(charset)).putByte(LF)
        .putByte(0)
        .result()
  }

}

private[lpd] final class LpdProtocol(fileSize: Long, queue: String, jobId: Int, hostname: String, filename: String) extends GraphStage[BidiShape[ByteString, ByteString, ByteString, ByteString]] {

  import LpdProtocol._

  private val lpdIn = Inlet[ByteString]("Lpd.in")
  private val lpdOut = Outlet[ByteString]("Lpd.out")

  private val tcpIn = Inlet[ByteString]("Tcp.in")
  private val tcpOut = Outlet[ByteString]("Tcp.out")

  private val bundledName = s"${"%03d".format(jobId)}$hostname"
  private val controlFile = buildControlFile(hostname, s"dfa$bundledName", filename)

  override def shape: BidiShape[ByteString, ByteString, ByteString, ByteString] = BidiShape.of(lpdIn, lpdOut, tcpIn, tcpOut)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var state: Int = 0
    private var nextState: Int = 0
    private var wasPulled: Boolean = false
    private var fileEndSent: Boolean = false

    private def sendState(): Unit = {
      // State Machine
      if (state == 0) {
        // State: 0
        // set initial state
        push(tcpOut, createBaseCommand(2, queue))
        nextState = 1
      } else if (state == 1) {
        // State: 1
        push(tcpOut, createCommand(2, s"cfa$bundledName", controlFile.size - 1))
        nextState = 2
      } else if (state == 2) {
        push(tcpOut, controlFile)
        nextState = 3
      } else if (state == 3) {
        push(tcpOut, createCommand(3, s"dfa$bundledName", fileSize))
        nextState = 4
      }
    }

    setHandler(lpdIn, new InHandler {
      override def onPush(): Unit = {
        val ele = grab(lpdIn)
        if (ele == EMPTY && state < 4) {
          state = nextState
          if (wasPulled) {
            sendState()
            wasPulled = false
          }
          push(lpdOut, ele)
        } else if (state == 4) {
          // in state 4 if we get a positive ACK we can safely timeout
          completeStage()
        } else {
          fail(lpdOut, new Exception(s"Could not ACK: ${ele.utf8String}"))
        }
      }

    })

    setHandler(lpdOut, new OutHandler {
      override def onPull(): Unit = {
        pull(lpdIn)
        if (state == 4 && state == nextState) {
          if (!isClosed(tcpIn)) {
            pull(tcpIn)
          }
        }
      }
    })

    setHandler(tcpIn, new InHandler {

      override def onUpstreamFinish(): Unit = {
        // if the file would even have a last chunk of 4096
        // we still need to sent a zero byte end
        if (!fileEndSent) {
          push(tcpOut, EMPTY)
        }
        complete(tcpOut)
      }

      override def onPush(): Unit = {
        val ele = grab(tcpIn)
        println(s"BS SIZE: ${ele.size}")
        if (ele.size < 4096) {
          fileEndSent = true
          push(tcpOut, ele ++ EMPTY)
        } else {
          push(tcpOut, ele)
        }
      }

    })

    setHandler(tcpOut, new OutHandler {
      override def onPull(): Unit = {
        if (state == nextState) {
          if (state < 4) {
            sendState()
          } else {
            // pulls data from the source
            if (!isClosed(tcpIn)) {
              pull(tcpIn)
            }
          }
        } else {
          wasPulled = true
        }
      }

    })

  }
}
