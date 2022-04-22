package client

import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{Actor}

import scala.util.Random

object Client {

  case class ActorStarted()

  case class Message(id: Int, message: String, topic: String)

  case class Subscribe(id: Int, topic: String)

  case class GetTopics(id: Int)

  case class Topics(id: Int, topics: Set[String])

  var ids: AtomicInteger = new AtomicInteger(0)

}

class Client extends Actor {

  import Client._

  val r: Random.type = scala.util.Random

  val id: Int = ids.getAndIncrement()

  var topic: String = ""

  def receive: Receive = {
    case Message(id, message, topic) => {
      println("Client" + this.id + ": " + message)
    }

    case Topics(id, topics) => {
      val topicId = r.nextInt(topic.length)

      topic = topics.toSeq(topicId)

      sender ! Subscribe(this.id, this.topic)

      println("Client" + this.id + " Selected Topic: " + this.topic)
    }

    case ActorStarted => {
      self ! GetTopics(this.id)
    }
  }

}
