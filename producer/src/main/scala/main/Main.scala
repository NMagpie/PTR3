package main


import akka.actor.{ActorRef, ActorSystem, Props}
import listener.ListenerSupervisor
import listener.ListenerSupervisor.CreateListener

import scala.io.StdIn.{readInt, readLine}

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var listenerSupervisor: Option[ActorRef] = None

  var isInteractive: Boolean = false

 def main(args: Array[String]): Unit = {

   listenerSupervisor = Option(system.actorOf(Props(classOf[ListenerSupervisor]), "supervisor"))

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
     listenerSupervisor.get ! CreateListener(1)
   } else {

     print("How many producers do you need: ")
     val number = readInt()

     for (i <- 1 to number)
       listenerSupervisor.get ! CreateListener((i % 2) + 1)
   }

 }

}
