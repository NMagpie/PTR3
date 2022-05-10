package network

import akka.actor.{Actor, OneForOneStrategy, Props, SupervisorStrategy}
import akka.io.{IO, Tcp}

import java.net.InetSocketAddress
import scala.language.postfixOps

class Server extends Actor {

  import akka.io.Tcp._
  import context.system

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Restart
  }

  var ids : Int = 0

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 8000))

  def receive: Receive = {
    case Bound(_) =>
      //context.parent ! b

    case CommandFailed(_: Bind) => context.stop(self)

    case c @ Connected(_, _) =>
      val connection = sender()
      val handler = context.actorOf(Props(classOf[MessagesHandler],connection),s"handler-$ids")
      handler ! c
      ids = ids + 1
      connection ! Register(handler)
  }

}
