package network

import akka.actor.{Actor, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import listener.Listener.WaitForInput
import main.Main.isInteractive
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.JsonDSL._

import java.net.InetSocketAddress
import scala.io.StdIn.readInt

object TcpClient {
  def props(remote: InetSocketAddress, replies: ActorRef): Props =
    Props(new TcpClient(remote, replies))
}

class TcpClient(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  import Tcp._
  import context.system

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  IO(Tcp) ! Connect(remote)

  var QoS: Int = -1

  def receive: Receive = {
    case a @ CommandFailed(_: Connect) =>
      listener ! a
      context.stop(self)

    case c @ Connected(_, _) =>
      val connection = sender()
      connection ! Register(self)

      if (isInteractive)
        while (QoS == -1) {
          print("Quality of Service [0-2]: ")
          QoS = readInt()
          if (QoS < 0 || QoS > 2)
            QoS = -1
        }
      else
        QoS = (new scala.util.Random).nextInt(3)

      connection ! Write(ByteString.fromString(Serialization.writePretty(("connectionType"->"Producer")~("QoS"->QoS))))

      listener ! c
      context.become {
        case data: ByteString =>
          connection ! Write(data)
        case CommandFailed(_: Write) =>
          listener ! "write failed"
        case Received(data) =>
          listener ! data
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          listener ! "connection closed"
          context.stop(self)
      }
  }
}