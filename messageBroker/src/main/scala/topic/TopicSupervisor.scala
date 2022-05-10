package topic

import akka.actor.{OneForOneStrategy, Props, SupervisorStrategy}
import akka.persistence.PersistentActor
import main.Main.topicPool
import network.MessagesHandler.Message
import topic.TopicSupervisor.CreateTopic

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object TopicSupervisor {
  case class CreateTopic(message: Message)
}

class TopicSupervisor extends PersistentActor {

  override def persistenceId: String = "TopicSupervisor"

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy(10, 10 seconds) {
    case _: Exception => SupervisorStrategy.Restart
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println("TopicSupervisor is restarting!")
  }

//  override def preStart(): Unit = {
//    super.preStart()
//    context.system.scheduler.scheduleOnce(1 minute) {
//      topicPool.values.head ! Kill
//    }
//  }

  def addTopic(name: String): Unit = {
    val topic = context.actorOf(Props(classOf[Topic], name), s"topic-$name")
    topicPool += (name -> topic)
  }

  val receiveRecover: Receive = {
    case CreateTopic(message) =>

      val name = message.topic

      if (!topicPool.contains(name))
        addTopic(name)

      topicPool(name) ! message

  }

  val receiveCommand: Receive = {

    case c @ CreateTopic(message) =>
      val name = message.topic
      if (!topicPool.contains(name)) {
        persist(c) ( c => {
          addTopic(c.message.topic)
          topicPool(name) ! message
        })
        //println(s"Topic $name was created")
      }

  }

}
