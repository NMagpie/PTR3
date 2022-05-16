package main

import akka.actor.{ActorRef, ActorSystem, Props}
import client.Overlord
import client.Overlord.CreateClients

import scala.io.StdIn.{readInt, readLine}

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var isInteractive: Boolean = false

  var overlord: Option[ActorRef] = None

  def main(args: Array[String]): Unit = {

    overlord = Option(system.actorOf(Props(classOf[Overlord]), "overlord"))

    var isIn: String = ""

    while (isIn == "") {
      isIn = readLine("Is producer interactive [y/n]: ")
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
  }

}
