package main

import akka.actor.{ActorRef, ActorSystem, Props}
import Overlord._

import scala.collection.mutable

/*

  MESSAGE BROKER

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

// Main method of the App, creates actor system, initializes Overlord and sends it messages about creation TCP-listener
// and topic Supervisor

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
