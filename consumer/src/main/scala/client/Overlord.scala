package client

import akka.actor.{Actor, ActorRef, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy}
import main.Main.system

object Overlord {
  case class CreateClients(number: Int)

  case class Exit()
}

class Overlord extends Actor {

  import Overlord._

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  var clients = Array.empty[ActorRef]

  override def receive: Receive = {
    case CreateClients(number) =>
      for (i <- 1 to number) {

        val actor = system.actorOf(Props(classOf[Client]), s"client$i")

        clients = clients :+ actor

      }

    case Exit =>
      for (actor <- clients)
        actor ! PoisonPill
  }
}