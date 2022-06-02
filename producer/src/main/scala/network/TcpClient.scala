package network

import akka.actor.{Actor, ActorRef, Cancellable, PoisonPill, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import main.Main.isInteractive
import network.TcpClient.{CommunicationMessage, Message}
import org.json4s.{Formats, NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.json4s.JsonDSL._
import java.net.InetSocketAddress

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn.readInt
import scala.language.postfixOps

object TcpClient {
  def props(remote: InetSocketAddress, replies: ActorRef): Props =
    Props(new TcpClient(remote, replies))

  trait CommunicationMessage {
    def id: Int
  }

  case class Message(id: Int, message: String, topic: String) extends CommunicationMessage

  case class Acknowledgement(id: Int, ackType: String) extends CommunicationMessage

  val printAck = ConfigFactory.load.getBoolean("printAck")
}

class TcpClient(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  import Tcp.{Connect, CommandFailed, Connected, Register, Write, Received, Close, ConnectionClosed}
  import context.system
  import TcpClient.{Acknowledgement, printAck}

  implicit val executor: ExecutionContextExecutor = system.dispatcher

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  IO(Tcp) ! Connect(remote)

  var QoS: Int = -1

  var connection: Option[ActorRef] = None

  def receive: Receive = {
    case a @ CommandFailed(_: Connect) =>
      listener ! a
      context.stop(self)

    case c @ Connected(_, _) =>
      connection = Option(sender())
      connection.get ! Register(self)

      if (isInteractive)
        while (QoS == -1) {
          print("Quality of Service [0-2]: ")
          QoS = readInt()
          if (QoS < 0 || QoS > 2)
            QoS = -1
        }
      else
        QoS = (new scala.util.Random).nextInt(3)

      connection.get ! Write(ByteString.fromString(Serialization.writePretty(("connectionType"->"Producer")~("QoS"->QoS))))

      listener ! c

      context.become {
        case a @ Message(_, _, _) =>
          val data = ByteString.fromString(Serialization.write(a))

          connection.get ! Write(data)

          if (printAck)
            println(s"${listener.path.name}[${a.id}] sen: message")

          if (QoS > 0)
            createResend(a)

        case data: ByteString =>

        case CommandFailed(_: Write) =>
          listener ! "write failed"

        case Received(data) =>
          val ack = parse(data.utf8String).extract[Acknowledgement]

          if (acks.contains(ack.id)) {

            acks(ack.id).cancel()

            resendTries -= ack.id

            QoS match {
              case 1 =>
                if (printAck)
                  println(s"${listener.path.name}[${ack.id}] rec: ack")

                acks -= ack.id
              case 2 =>
                ack.ackType match {
                  case "recAck" =>
                    if (printAck)
                      println(s"${listener.path.name}[${ack.id}] rec: recAck\n${listener.path.name}[${ack.id}] sen: senAck")

                    val jsonAck = Serialization.write(Acknowledgement(ack.id, "senAck"))

                    createResend(Acknowledgement(ack.id, "senAck"))

                    connection.get ! Write(ByteString.fromString(jsonAck))

                  case "done" =>
                    if (printAck)
                      println(s"${listener.path.name}[${ack.id}] rec: doneAck")

                    acks(ack.id).cancel()

                    acks -= ack.id
                  case _ =>
                }
            }
          }

        case "close" =>
          connection.get ! Close
        case _: ConnectionClosed =>
          //listener ! "connection closed"
          for (ack <- acks.values)
            ack.cancel()
          context.stop(self)
      }
  }

  var resendTries : Map[Int, Int] = Map.empty[Int, Int]

  var acks: Map[Int, Cancellable] = Map.empty[Int, Cancellable]

  def createResend(message: CommunicationMessage) : Unit = {
    val id = message.id

    resendTries += (id -> 0)

    acks += (id -> system.scheduler.schedule(5 second, 5 second) {
      if (printAck)
        println(s"${listener.path.name}[${id}] res: $message")

      connection.get ! Write(ByteString.fromString(Serialization.write(message)))

      resendTries = resendTries + (id -> (resendTries(id) + 1))

      if (resendTries(id) == 20) {
        println("Number of retries has been exceeded, turning down connection.")
        connection.get ! Close
        listener ! PoisonPill
      }
    })
  }

}