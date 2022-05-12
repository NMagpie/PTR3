package network

import akka.actor.{Actor, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import client.Client.Message
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse

import java.net.InetSocketAddress
import scala.util.Random

object TcpClient {
  def props(remote: InetSocketAddress, replies: ActorRef, id: Int): Props =
    Props(new TcpClient(remote, replies, id))
}

class TcpClient(remote: InetSocketAddress, listener: ActorRef, id: Int) extends Actor {

  import akka.io.Tcp._
  import context.system

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  IO(Tcp) ! Connect(remote)

  val r: Random.type = scala.util.Random

  var selectedTopics: Set[String] = Set.empty[String]

  var connection: Option[ActorRef] = None

  def selectingTopics: Receive = {

    case Received(data) =>
      val jsonTopics = parse(data.utf8String)
      val topics = (jsonTopics \ "topics").extract[Set[String]]
      for (topic <- topics)
        if (r.nextInt(100) > 85
          //&& topic != "en"
        ) {
          selectedTopics += topic
        }

      //selectedTopics += "de"

      println(s"Client${this.id}: Selected topics: $selectedTopics")

      connection.get ! Write(ByteString.fromString(Serialization.write("topics"->selectedTopics)))

      context.become(receiveMessages)

    case PeerClosed => context.stop(self)

    case a @ _ => println(a)
  }

  def receiveMessages: Receive = {
    case data: ByteString =>
      connection.get ! Write(data)
    case CommandFailed(_: Write) =>
      // O/S buffer was full
      listener ! "write failed"
    case Received(data) =>
      val jsonMessage = parse(data.utf8String)
      val id = (jsonMessage \ "id").extract[Int]
      val topic = (jsonMessage \ "topic").extract[String]
      val message = (jsonMessage \ "message").extract[String]
      listener ! Message(id, message, topic)
    case "close" =>
      connection.get ! Close
    case _: ConnectionClosed =>
      listener ! "connection closed"
      context.stop(self)
    case _ =>
  }

  def receive: Receive = {
    case CommandFailed(_: Connect) =>
      listener ! "connect failed"
      context.stop(self)

    case Connected(_, _) =>
      val connection = sender()

      connection ! Register(self)

      connection ! Write(ByteString.fromString(Serialization.write("connectionType"->"Consumer")))

      this.connection = Option(connection)

      context.become(selectingTopics)
    case a @ _ => println(a)
  }
}
