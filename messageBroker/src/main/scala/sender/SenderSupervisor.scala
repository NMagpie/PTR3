package sender

import akka.actor.Actor

object SenderSupervisor {

  case object CreateSender

}

class SenderSupervisor extends Actor {

  def receive: Receive = ???

}