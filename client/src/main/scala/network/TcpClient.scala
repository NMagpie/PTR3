package network

import akka.actor.{Actor, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import client.Client.Message
import main.Main.isInteractive
import network.TcpClient.Acknowledgement
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.json4s.JsonDSL._

import java.net.InetSocketAddress
import scala.io.StdIn.{readInt, readLine}
import scala.util.Random

object TcpClient {
  def props(remote: InetSocketAddress, replies: ActorRef, id: Int): Props =
    Props(new TcpClient(remote, replies, id))

  case class Acknowledgement(id: Int, ackType: String)
}

class TcpClient(remote: InetSocketAddress, listener: ActorRef, id: Int) extends Actor {

  import akka.io.Tcp._
  import context.system

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  IO(Tcp) ! Connect(remote)

  val r: Random = new scala.util.Random

  var QoS: Int = -1

  var qos2Messages: Map[Int, ByteString] = Map.empty[Int, ByteString]

  var selectedTopics: Set[String] = Set.empty[String]

  var connection: Option[ActorRef] = None

  def selectingTopics: Receive = {

    case Received(data) =>
      val jsonTopics = parse(data.utf8String)
      val topics = (jsonTopics \ "topics").extract[Set[String]]

      if (isInteractive) {
        val selectingTopics = readLine(s"Choose topics or leave empty to subscribe to all the topics:\n $topics\n")

        if (selectingTopics == "")
          selectedTopics = topics
        else
          selectedTopics = selectingTopics.split(" ").toSet
      } else {
        for (topic <- topics)
          if (r.nextInt(100) > 85
          //&& topic != "en"
          ) {
            selectedTopics += topic
          }

        //selectedTopics += "de"

        println(s"Client${this.id}: Selected topics: $selectedTopics")
      }

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
      QoS match {
        case 0 =>
          processMessage(data)

        case 1 =>
          processMessage(data)
          val jsonMessage = parse(data.utf8String)
          val id = (jsonMessage \ "id").extract[Int]
          val ack = ByteString.fromString(Serialization.write(Acknowledgement(id, "ack")))
          connection.get ! Write(ack)

        case 2 =>
          var ackType = ""

          val jsonMessage = parse(data.utf8String)
          val id = (jsonMessage \ "id").extract[Int]

          if (data.utf8String.contains("senAck")) {
            if (qos2Messages.contains(id))
            processMessage(qos2Messages(id))
            ackType = "done"
            qos2Messages -= id
          } else {
            ackType = "recAck"
            qos2Messages += (id -> data)
          }

          val ack = ByteString.fromString(Serialization.write(Acknowledgement(id, ackType)))
          connection.get ! Write(ack)
      }
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

      if (isInteractive)
        while (QoS == -1) {
          print("Quality of Service [0-2]: ")
          QoS = readInt()
          if (QoS < 0 || QoS > 2)
            QoS = -1
        }
      else
        QoS = r.nextInt(3)

      connection ! Write(ByteString.fromString(Serialization.write(("connectionType"->"Consumer") ~ ("QoS"->QoS))))

      this.connection = Option(connection)

      context.become(selectingTopics)
    case a @ _ => println(a)
  }

  def processMessage(data: ByteString): Unit = {
    val jsonMessage = parse(data.utf8String)
    val id = (jsonMessage \ "id").extract[Int]
    val topic = (jsonMessage \ "topic").extract[String]
    val message = (jsonMessage \ "message").extract[String]
    listener ! Message(id, message, topic)
  }
}
