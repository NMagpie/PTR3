package sender

import akka.actor.Actor

object SenderManager{

  case object Test

}

class SenderManager extends Actor {

  def receive: Receive = ???

}