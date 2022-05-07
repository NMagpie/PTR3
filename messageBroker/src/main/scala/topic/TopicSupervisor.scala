package topic

import akka.actor.{Actor, OneForOneStrategy, Props, SupervisorStrategy}
import main.Main.{system, topicPool}
import network.MessagesHandler.Message
import topic.TopicSupervisor.CreateTopic

object TopicSupervisor {
  case class CreateTopic(message: Message)
}

class TopicSupervisor extends Actor {

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  override def receive: Receive = {
    case CreateTopic(message) =>
      val name = message.topic
      if (!topicPool.contains(name)) {
        val topic = system.actorOf(Props(classOf[Topic], name), s"topic-$name")
        topicPool += (name -> topic)
        //println(s"Topic $name was created")
      }

      topicPool(name) ! message

  }

}
