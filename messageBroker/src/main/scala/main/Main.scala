package main

import akka.actor.{ActorRef, ActorSystem, Props}

import Overlord._

import scala.collection.mutable

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var topicPool: mutable.Map[String, ActorRef] = mutable.Map[String, ActorRef]()

  var topicSup : Option[ActorRef] = None

  var overlord : Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    overlord.get ! CreateTopicSup

    overlord.get ! CreateServer

  }
}
