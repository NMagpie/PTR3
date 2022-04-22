package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import client.Overlord
import client.Overlord.CreateClients

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var overlord: Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    //implicit val mat: ActorMaterializer = ActorMaterializer()

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    overlord.get ! CreateClients(3)
  }

}
