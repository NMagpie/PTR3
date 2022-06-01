package main

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import main.Main.system.{dispatcher, scheduler}
import main.Overlord._

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/*

  MESSAGE BROKER

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

// Main method of the App, creates actor system, initializes Overlord and sends it messages about creation TCP-listener
// and topic Supervisor

object Main {

  var stable = true

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var topicPool: mutable.Map[String, ActorRef] = mutable.Map[String, ActorRef]()

  var topicSup: Option[ActorRef] = None

  var overlord: Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    overlord.get ! CreateTopicSup

    overlord.get ! CreateServer

    if (!ConfigFactory.load.getBoolean("stable")) {
      scheduler.schedule(2 seconds, 2 seconds) {
        stable = !stable
        if (stable)
          println("Up")
        else
          println("Down")
      }
    }

  }
}
