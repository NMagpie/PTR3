package network

import akka.actor.{Actor, ActorRef, Cancellable, PoisonPill, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import client.Client.Message
import com.typesafe.config.ConfigFactory
import main.Main.{isInteractive, stable, system}
import network.TcpClient.printAck
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.json4s.JsonDSL._
import java.net.InetSocketAddress

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn.{readInt, readLine}
import scala.language.postfixOps
import scala.util.Random

object TcpClient {

  def props(remote: InetSocketAddress, replies: ActorRef, id: Int): Props =
    Props(new TcpClient(remote, replies, id))

  case class Acknowledgement(id: Int, ackType: String)

  val printAck = ConfigFactory.load.getBoolean("printAck")
}

class TcpClient(remote: InetSocketAddress, listener: ActorRef, id: Int) extends Actor {

  import akka.io.Tcp._
  import context.system
  import TcpClient.Acknowledgement

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  implicit val executor: ExecutionContextExecutor = system.dispatcher

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
          ) {
            selectedTopics += topic
          }

        println(s"Client${this.id}: Selected topics: $selectedTopics")
      }

      connection.get ! Write(ByteString.fromString(Serialization.write("topics" -> selectedTopics)))

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
    if (stable) {
      QoS match {
        case 0 =>
          if (printAck)
            println(s"${self.path.name} rec: message")
          processMessage(data)

        case 1 =>
          processMessage(data)
          val jsonMessage = parse(data.utf8String)
          val id = (jsonMessage \ "id").extract[Int]
          val ack = ByteString.fromString(Serialization.write(Acknowledgement(id, "ack")))

          if (printAck)
            println(s"${self.path.name}[$id] rec: message\n${self.path.name}[$id] sen: ack\n")
          connection.get ! Write(ack)

        case 2 =>
          var ackType = ""

          val jsonMessage = parse(data.utf8String)
          val id = (jsonMessage \ "id").extract[Int]

          if (data.utf8String.contains("senAck")) {
            if (acks.contains(id)) {
              acks(id).cancel()
              acks -= id
              resendTries -= id
            }
            if (printAck)
              println(s"${self.path.name}[$id] rec: senAck\n${self.path.name}[$id] sen: done\n")
            if (qos2Messages.contains(id))
              processMessage(qos2Messages(id))
            ackType = "done"

            qos2Messages -= id
          } else {
            if (printAck)
              println(s"${self.path.name}[$id] rec: message\n${self.path.name}[$id] sen: recAck\n")
            ackType = "recAck"
            qos2Messages += (id -> data)
            if (!acks.contains(id)) {
              createResend(Acknowledgement(id, ackType))
            }
          }

          val ack = ByteString.fromString(Serialization.write(Acknowledgement(id, ackType)))

          connection.get ! Write(ack)
      }
    }
    case "close" =>
      connection.get ! Close
    case _: ConnectionClosed =>
      //listener ! "connection closed"
      for (ack <- acks.values)
        ack.cancel()
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

  var resendTries : Map[Int, Int] = Map.empty[Int, Int]

  var acks: Map[Int, Cancellable] = Map.empty[Int, Cancellable]

  def createResend(message : Acknowledgement) : Unit = {
    val id = message.id

    resendTries += (id -> 0)

    acks += (id -> system.scheduler.schedule(5 second, 5 second) {
      if (printAck)
        println(s"${self.path.name}[${id}] res: $message")

      connection.get ! Write(ByteString.fromString(Serialization.write(message)))

      resendTries = resendTries + (id -> (resendTries(id) + 1))

      if (resendTries(id) == 20) {
        println(s"[${self.path.name}] Number of retries has been exceeded, turning down connection.")
        connection.get ! Close
        listener ! PoisonPill
      }
    })
  }

}
