package main

import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}
import network.Server
import topic.TopicSupervisor

object Overlord {

  case class CreateServer()

  case class CreateTopicSup()

}

class Overlord extends Actor {

  import Overlord._

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  var server : Option[ActorRef] = None

  var topicSup : Option[ActorRef] = None

  def receive: Receive = {
    case CreateServer =>
      if (server.isEmpty) {
        println("Server was created")
        server = Option(context.actorOf(Props(classOf[Server]), "server"))
      }

    case CreateTopicSup =>
      if (topicSup.isEmpty) {
        println("TopicSupervisor was created")
        topicSup = Option(context.actorOf(Props(classOf[TopicSupervisor]), "topicSupervisor"))
        Main.topicSup = topicSup
      }
  }

}