package network

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.io.Tcp
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import main.Main.{stable, topicPool, topicSup}
import network.MessagesHandler.CommunicationMessage
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.json4s.{Formats, NoTypeHints, jackson}
import topic.Topic.{Subscribe, Unsubscribe}
import topic.TopicSupervisor.CreateTopic

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/*

  Message Handler - the most complex part of the Message Broker. It can have behavior of two types - Producer or Consumer.
  Also, can persist its state (which type of connection he has), which topics he is subscribed to and also messages he
  sends (if it is a Consumer). If message was not acknowledged in 100 milliseconds, takes place its resending,
  otherwise message is no longer being persisted. If last 100 tries of resending were failed - actor just closes connection.

 */

object MessagesHandler {

  //////////////////////////////////////////////

  trait CommunicationMessage {
    def id: Int
  }

  case class Message(id: Int, message: String, topic: String) extends CommunicationMessage

  case class SubscribeToAll()

  case class GiveTopics()

  case class Acknowledgement(id: Int, ackType: String) extends CommunicationMessage

  val printAck = ConfigFactory.load.getBoolean("printAck")

}

class MessagesHandler(connection: ActorRef) extends Actor {

  import MessagesHandler.{Message, _}
  import Tcp._
  import main.Main.system

  implicit val executor: ExecutionContextExecutor = system.dispatcher

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  var QoS: Int = 0

  var qos2Messages: Map[Int, ByteString] = Map.empty[Int, ByteString]

  val idPrefix: Int = self.path.name.substring(8).toInt * 1_000

  def processMessage(data: ByteString): Unit = {
    val message = parse(data.utf8String).extract[Message]

    val id = if (message.id > 1_000) {
      java.lang.Math.floorMod(message.id, 1_000)
    } else {
      message.id
    }

    val messUnique = Message(idPrefix + id, message.message, message.topic)

    if (topicPool.contains(messUnique.topic)) {
      topicPool(messUnique.topic) ! messUnique
    } else {
      topicSup.get ! CreateTopic(messUnique)
    }
  }

  def producerHandler: Receive = {
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
            connection ! Write(ack)

          case 2 =>
            var ackType = ""

            val jsonMessage = parse(data.utf8String)
            val id = (jsonMessage \ "id").extract[Int]

            if (data.utf8String.contains("senAck")) {
              if (acks.contains(id)) {
                acks(id).cancel()
                acks -= id
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
            connection ! Write(ack)
        }
      }

    case Connected =>

    case PeerClosed => endProducer()

    case ErrorClosed(_) => endProducer()
  }

  def consumerHandler(topics: Set[String]): Receive = {

    case a@Message(_, _, _) =>
      connection ! Write(ByteString.fromString(Serialization.write(a)))
      if (printAck)
        println(s"${self.path.name}[${a.id}] sen: message")

      if (QoS > 0)
        createResend(a)

    case SubscribeToAll =>
      for (topic <- topics) {
        if (topicPool.contains(topic))
          topicPool(topic) ! Subscribe(self)
      }

    case Received(data) =>
      if (stable) {
        val ack = parse(data.utf8String).extract[Acknowledgement]

        if (acks.contains(ack.id)) {
          resendTries = 0

          acks(ack.id).cancel()

          QoS match {
            case 1 =>
              if (printAck)
                println(s"${self.path.name}[${ack.id}] rec: ack")
              acks -= ack.id
            case 2 =>
              ack.ackType match {
                case "recAck" =>
                  if (printAck)
                    println(s"${self.path.name}[${ack.id}] rec: recAck\n${self.path.name}[${ack.id}] sen: senAck\n")
                  val jsonAck = Serialization.write(Acknowledgement(ack.id, "senAck"))

                  createResend(ack)

                  connection ! Write(ByteString.fromString(jsonAck))
                case "done" =>
                  if (printAck)
                    println(s"${self.path.name}[${ack.id}] rec: doneAck")
                  acks(ack.id).cancel()

                  acks -= ack.id
                case _ =>
              }
          }
        }
      }

    case Connected =>

    case Closed =>
      endConsumer(topics)

    case Aborted =>
      endConsumer(topics)

    case ConfirmedClosed =>
      endConsumer(topics)

    case PeerClosed =>
      endConsumer(topics)

    case ErrorClosed(_) =>
      endConsumer(topics)

    case a@_ => println(a)
  }

  def selectingTopics: Receive = {
    case GiveTopics =>
      val jsonTopics = Serialization.write("topics" -> topicPool.keySet)

      connection ! Write(ByteString.fromString(jsonTopics))

    case Received(data) =>
      val jsonTopics = parse(data.utf8String)
      val topics = (jsonTopics \ "topics").extract[Set[String]]

      cancelTimeout()

      self ! SubscribeToAll

      context.become(consumerHandler(topics))

    case Connected =>

    case Closed =>
      context.stop(self)

    case Aborted =>
      context.stop(self)

    case ConfirmedClosed =>
      context.stop(self)

    case PeerClosed =>
      context.stop(self)

    case ErrorClosed(_) =>
      context.stop(self)

    case _ =>
  }

  def endConsumer(topics: Set[String]): Unit = {
    for (topic <- topics) {
      if (topicPool.contains(topic))
        topicPool(topic) ! Unsubscribe(self)
    }
    for (ack <- acks.values)
      ack.cancel()
    context.stop(self)
  }

  def endProducer() : Unit = {
    for (ack <- acks.values)
      ack.cancel()
    context.stop(self)
  }

  var timeOutTask: Option[Cancellable] = None

  def scheduleTimeout(delay: Int = 5): Unit = {
    timeOutTask = Option(system.scheduler.scheduleOnce(delay seconds) {
      connection ! ConfirmedClose
      context.stop(self)
    })
  }

  def cancelTimeout(): Unit = {
    if (timeOutTask.isDefined) {
      timeOutTask.get.cancel()
      timeOutTask = None
    }
  }

  override def receive: Receive = {

    case Received(data) =>
      val connType = parse(data.utf8String)

      (connType \ "connectionType").extract[String] match {
        case "Producer" =>
          cancelTimeout()

          context.become(producerHandler)

        case "Consumer" =>
          cancelTimeout()

          scheduleTimeout()

          context.become(selectingTopics)

          self ! GiveTopics

        case _ =>
      }

      QoS = (connType \ "QoS").extract[Int]

    case Connected(_, _) =>
      scheduleTimeout()

    case Closed =>
      context.stop(self)

    case Aborted =>
      context.stop(self)

    case ConfirmedClosed =>
      context.stop(self)

    case PeerClosed =>
      context.stop(self)

    case ErrorClosed(_) =>
      context.stop(self)
  }

  var resendTries: Int = 0

  var acks: Map[Int, Cancellable] = Map.empty[Int, Cancellable]

  def createResend(message: CommunicationMessage): Unit = {
    acks += (message.id -> system.scheduler.schedule(750 milliseconds, 750 milliseconds) {
      if (printAck)
        println(s"${self.path.name}[${message.id}] res: $message")
      connection ! Write(ByteString.fromString(Serialization.write(message)))
      resendTries = resendTries + 1
      if (resendTries == 25) {
        println(s"[${self.path.name}] Number of retries has been exceeded, turning down connection.")
        connection ! Close
        context.stop(self)
      }
    })
  }

}
