package main

import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}
import listener.ListenerSupervisor
import sender.{SenderManager, SenderScaler, SenderSupervisor}

object MainSupervisor {

  case object CreateListSup

  case object CreateSendSup

  case object CreateSendScaler

  case object CreateSendMan

}

class MainSupervisor extends Actor {

  import MainSupervisor._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  self ! CreateListSup

  self ! CreateSendMan

  self ! CreateSendSup

  self ! CreateSendScaler

  var listSup : Option[ActorRef] = None

  var sendMan : Option[ActorRef] = None

  var sendSup : Option[ActorRef] = None

  var sendScaler : Option[ActorRef] = None

  def receive: Receive = {
    case CreateListSup =>
      if (listSup.isEmpty) {
        println("ListenerSupervisor was created")
        listSup = Option(context.actorOf(Props(classOf[ListenerSupervisor]), "listenerSupervisor"))
      }

    case CreateSendMan =>
      if (sendMan.isEmpty) {
        println("SenderManager was created")
        sendMan = Option(context.actorOf(Props(classOf[SenderManager]), "senderManager"))
      }

    case CreateSendSup =>
      if (sendSup.isEmpty) {
        println("SenderSupervisor was created")
        sendSup = Option(context.actorOf(Props(classOf[SenderSupervisor]), "senderSupervisor"))
      }

    case CreateSendScaler =>
      if (sendScaler.isEmpty) {
        println("SenderScaler was created")
        sendScaler = Option(context.actorOf(Props(classOf[SenderScaler]), "senderScaler"))
      }
  }

}