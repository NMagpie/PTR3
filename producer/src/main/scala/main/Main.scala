package main


import akka.actor.{ActorRef, ActorSystem, Props}
import listener.ListenerSupervisor
import listener.ListenerSupervisor.CreateListener

object Main {

  implicit val system: ActorSystem = ActorSystem("mainSystem")

  var listenerSupervisor: Option[ActorRef] = None

 def main(args: Array[String]): Unit = {

   listenerSupervisor = Option(system.actorOf(Props(classOf[ListenerSupervisor]), "supervisor"))

   listenerSupervisor.get ! CreateListener(1)

   listenerSupervisor.get ! CreateListener(2)
 }

}
