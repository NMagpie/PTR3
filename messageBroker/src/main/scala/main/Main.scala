package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import listener.Listener.Message

import Overlord._

import scala.collection.mutable

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var messageBuffers: mutable.Map[String, Array[Message]] = mutable.Map[String, Array[Message]]()

  var topicBuffer = Set.empty[String]

  var overlord : Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    implicit val mat: ActorMaterializer = ActorMaterializer()

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    overlord.get ! CreateListSup

    overlord.get ! CreateSendMan

    overlord.get ! CreateSendSup

    overlord.get ! CreateSendScaler

  }
}
