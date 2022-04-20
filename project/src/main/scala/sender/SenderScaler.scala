package sender

import akka.actor.Actor

object SenderScaler {

  case object Scale

}

class SenderScaler extends Actor {

  def receive: Receive = ???

}