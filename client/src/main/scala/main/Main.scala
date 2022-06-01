package main

import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import client.Overlord
import client.Overlord.CreateClients
import com.typesafe.config.ConfigFactory
import main.Main.system.{dispatcher, scheduler}

import scala.concurrent.duration.DurationInt
import scala.io.StdIn.{readInt, readLine}
import scala.language.postfixOps

object Main {

  var stable = true

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var isInteractive: Boolean = false

  var overlord: Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    var isIn: String = ""

    while (isIn == "") {
      isIn = readLine("Is consumer interactive [y/n]: ")
      isIn match {
        case "y" | "Y" => isInteractive = true
        case "n" | "N" => isInteractive = false
        case _ => isIn = ""
      }
    }

    if (isInteractive) {
      overlord.get ! CreateClients(1)
    } else {

      print("How many producers do you need: ")
      val number = readInt()

      overlord.get ! CreateClients(number)
    }

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
