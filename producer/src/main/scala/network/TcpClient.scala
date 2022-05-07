package network

import akka.actor.{Actor, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization

import java.net.InetSocketAddress

object TcpClient {
  def props(remote: InetSocketAddress, replies: ActorRef): Props =
    Props(new TcpClient(remote, replies))
}

class TcpClient(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  import Tcp._
  import context.system

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  IO(Tcp) ! Connect(remote)

  def receive: Receive = {
    case a @ CommandFailed(_: Connect) =>
      listener ! a
      context.stop(self)

    case c @ Connected(_, _) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      connection ! Write(ByteString.fromString(Serialization.writePretty("connectionType"->"Producer")))
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