package topic

import akka.actor.{OneForOneStrategy, Props, SupervisorStrategy}
import akka.persistence.PersistentActor
import main.Main.{system, topicPool}
import network.MessagesHandler.Message
import topic.TopicSupervisor.CreateTopic

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/*

  Topic Supervisor - creates and supervises all the Topic Actors.
  Persists list of Topic Actors, and restarts it in case of exception, failure or restart.

 */

object TopicSupervisor {
  case class CreateTopic(message: Message)
}

class TopicSupervisor extends PersistentActor {

  implicit val executor: ExecutionContextExecutor = system.dispatcher

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
  //    context.system.scheduler.scheduleOnce(2 minutes) {
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

    case c@CreateTopic(message) =>
      val name = message.topic
      if (!topicPool.contains(name)) {
        persist(c)(c => {
          addTopic(c.message.topic)
          topicPool(name) ! message
        })
        //println(s"Topic $name was created")
      }

  }

}
