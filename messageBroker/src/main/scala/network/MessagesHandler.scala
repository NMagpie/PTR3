package network

import akka.actor.{ActorRef, Cancellable, Kill}
import akka.io.Tcp
import akka.persistence.{DeleteMessagesSuccess, DeleteSnapshotSuccess, PersistentActor, SnapshotOffer, SnapshotSelectionCriteria}
import akka.util.ByteString
import main.Main.{topicPool, topicSup}
import network.MessagesHandler.{Acknowledgement, ConnectionType, GiveTopics, SubscribeToAll, TopicsSelected}
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
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
  case class Message(id: Int, message: String, topic: String)

  case class SubscribeToAll()

  case class GiveTopics()

  case class ConnectionType(connType: ByteString)

  case class TopicsSelected(topics: ByteString)

  case class Acknowledgement(id: Int)

}

class MessagesHandler(connection: ActorRef) extends PersistentActor {
  import Tcp._
  import main.Main.system
  import MessagesHandler.Message

  override def persistenceId: String = self.path.name

//    override def preStart(): Unit = {
//      super.preStart()
//      if (self.path.name == "handler-2")
//      context.system.scheduler.scheduleOnce(2 minutes) {
//        self ! Kill
//      }
//    }

  implicit val executor: ExecutionContextExecutor = system.dispatcher

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  def producerHandler: Receive = {
    case Received(data) =>
      val message = parse(data.utf8String).extract[Message]

      if (topicPool.contains(message.topic)) {
        topicPool(message.topic) ! message
      } else {
        topicSup.get ! CreateTopic(message)
      }

    case Connected =>

    case PeerClosed => context.stop(self)

    case ErrorClosed(_) => context.stop(self)
  }

  def consumerHandler(topics : Set[String]): Receive = {

    case a @ Message(_, _, _) =>
        connection ! Write(ByteString.fromString(Serialization.write(a)))
        createResend(a)

    case SubscribeToAll =>
      for (topic <- topics) {
        if (topicPool.contains(topic))
          topicPool(topic) ! Subscribe(self)
      }

    case Received(data) =>
      val ack = parse(data.utf8String).extract[Acknowledgement]

      if (acks.contains(ack.id)) {
        resendTries = 0
        acks(ack.id).cancel()
        acks -= ack.id
        ackMes -= ack.id
        deleteSnapshots(SnapshotSelectionCriteria())
        saveSnapshot(ackMes)
      }

      println(s"Ack[${ack.id}]")

    case DeleteMessagesSuccess =>

    case DeleteSnapshotSuccess =>

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

    case a @ _ => println(a)
  }

  def selectingTopics: Receive = {
    case GiveTopics =>
      val jsonTopics = Serialization.write("topics"->topicPool.keySet)

      connection ! Write(ByteString.fromString(jsonTopics))

    case Received(data) =>
      persist(TopicsSelected(data)) { _ =>
        deleteMessages(1)
        selectTopics(data)
      }

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
      topicPool(topic) ! Unsubscribe(self)
    }
    for (ack <- acks.values)
      ack.cancel()
    println("Connection closed, unsubscribed.")
    deleteMessages(lastSequenceNr)
    deleteSnapshots(SnapshotSelectionCriteria())
    context.stop(self)
  }

  var timeOutTask : Option[Cancellable] = None

  def scheduleMessage(): Unit = {

  }

  def scheduleTimeout(delay : Int = 5): Unit = {
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

  override def receiveRecover: Receive = {

    case ConnectionType(data) =>
      connType(data)

    case TopicsSelected(data) =>
      selectTopics(data)

    case SnapshotOffer(_, snapshot: Map[Int, Message]) =>
      ackMes = snapshot
      for (message <- ackMes)
        self ! message._2

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

  override def receiveCommand: Receive = {

    case Received(data) =>
      persist(ConnectionType(data)) ( _ => connType(data))

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

  def selectTopics(data: ByteString): Unit = {
    val jsonTopics = parse(data.utf8String)
    val topics = (jsonTopics \ "topics").extract[Set[String]]

    cancelTimeout()

    self ! SubscribeToAll

    context.become(consumerHandler(topics))
  }

  def connType(data: ByteString): Unit = {
      val connType = parse(data.utf8String)

      (connType \ "connectionType").extract[String] match {
        case "Producer" =>
          cancelTimeout()

          context.become(producerHandler)

        case "Consumer" =>
          cancelTimeout()

          scheduleTimeout()

          self ! GiveTopics

          context.become(selectingTopics)

        case _ =>
      }
  }

  var resendTries : Int = 0;

  var acks: Map[Int, Cancellable] = Map.empty[Int, Cancellable]

  var ackMes : Map[Int, Message] = Map.empty[Int ,Message]

  def createResend(message : Message) : Unit = {
    acks += (message.id -> system.scheduler.schedule(100 milliseconds, 100 milliseconds){
      connection ! Write(ByteString.fromString(Serialization.write(message)))
      resendTries = resendTries + 1
    })
    ackMes += (message.id -> message)
    deleteSnapshots(SnapshotSelectionCriteria())
    saveSnapshot(ackMes)
  }

}
