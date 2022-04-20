package listener

import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}
import listener.ListenerSupervisor.CreateListener

object ListenerSupervisor {

  case class CreateListener(number: Int)

}

class ListenerSupervisor extends Actor {

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  self ! CreateListener(1)

  self ! CreateListener(2)

  var firstListener: Option[ActorRef] = None

  var secondListener: Option[ActorRef] = None

  def receive: Receive = {
    case CreateListener(number) =>
      number match {
        case 1 =>
          if (firstListener.isEmpty) {
            println("FirstListener was created")
            firstListener = Option(context.actorOf(Props(classOf[Listener], number), "firstListener"))
          }

        case 2 =>
          if (secondListener.isEmpty) {
            println("SecondListener was created")
            secondListener = Option(context.actorOf(Props(classOf[Listener], number), "secondListener"))
          }
      }
  }

}
