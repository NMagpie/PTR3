package listener

import akka.actor.{Actor, ActorRef, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy}
import listener.Listener.OpenSource
import listener.ListenerSupervisor.CreateListener
import main.Main.{messageBuffers, system, topicBuffer}

import scala.concurrent.duration.DurationInt

object ListenerSupervisor {

  case class CreateListener(number: Int)

}

class ListenerSupervisor extends Actor {

  import context.dispatcher

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  var firstListener: Option[ActorRef] = None

  var secondListener: Option[ActorRef] = None

//  system.scheduler.scheduleOnce(10.seconds) {
//    firstListener.get ! PoisonPill
//    secondListener.get ! PoisonPill
//
//    for (topicBuffer <- messageBuffers) {
//      println("Topic: " + topicBuffer._1)
//      for (topicMessage <- topicBuffer._2) {
//      println("\t\t"+topicMessage)
//      println()
//      }
//    }
//
//    println(topicBuffer)
//  }

  def receive: Receive = {
    case CreateListener(number) =>
      number match {
        case 1 =>
          if (firstListener.isEmpty) {
            println("FirstListener was created")
            firstListener = Option(system.actorOf(Props(classOf[Listener], number), "firstListener"))
          }

        case 2 =>
          if (secondListener.isEmpty) {
            println("SecondListener was created")
            secondListener = Option(system.actorOf(Props(classOf[Listener], number), "secondListener"))
          }
      }
  }

}
