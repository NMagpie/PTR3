package topic

import akka.actor.{Actor, ActorRef}
import network.MessagesHandler.Message
import topic.Topic.{Subscribe, Unsubscribe}

import scala.collection.mutable

object Topic {
  case class Subscribe(subscriber: ActorRef)

  case class Unsubscribe(subscriber: ActorRef)

  case class PreviousMessages(buffer: mutable.Queue[Message])
}

class Topic(name: String) extends Actor {

  //val router: Router = Router(BroadcastRoutingLogic(), Vector[ActorRefRoutee]())

  var router: Set[ActorRef] = Set.empty[ActorRef]

  override def receive: Receive = {
    case a @ Message(id, message, topic) => {
      router.foreach(consumer => consumer ! a)
      //router.route(a, self)
      //println(s"Topic $name: \'${message}\'")
    }

    case Subscribe(subscriber) => {
      router += subscriber
      //router.addRoutee(subscriber)
      //subscriber ! PreviousMessages(buffer)
    }

    case Unsubscribe(subscriber) =>
      router -= subscriber
      //router.removeRoutee(subscriber)

    case _ =>
  }
}
