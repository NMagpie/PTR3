package sender

import akka.actor.Actor

object Sender {

  case class SendData(json: String)

}

class Sender extends Actor {

  def receive: Receive = ???

}