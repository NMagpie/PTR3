package client

import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{Actor, ActorRef}
import com.typesafe.config.ConfigFactory
import main.Main.system
import network.TcpClient
import org.json4s.{Formats, NoTypeHints, jackson}

import java.net.InetSocketAddress
import scala.util.Random

object Client {

  case class Message(id: Int, message: String, topic: String)

  var ids: AtomicInteger = new AtomicInteger(0)

}

class Client extends Actor {

  import Client._

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  val r: Random.type = scala.util.Random

  val id: Int = ids.getAndIncrement()
  
  val client: ActorRef = system.actorOf(TcpClient.props(new InetSocketAddress(ConfigFactory.load.getString("messagebroker"),8000), self, this.id),s"tcp-client${this.id}")

  var topics: Set[String] = Set.empty[String]

  def receive: Receive = {
    case Message(id, message, topic) =>
      println("Client" + this.id + s"[$topic]:[$id] $message")
  }

}
