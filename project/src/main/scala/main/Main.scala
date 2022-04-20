package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer

object Main {
  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var mainSupervisor : Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    implicit val mat: ActorMaterializer = ActorMaterializer()

    val mainSupervisor = system.actorOf(Props(classOf[MainSupervisor]), "mainSupervisor")

  }
}
