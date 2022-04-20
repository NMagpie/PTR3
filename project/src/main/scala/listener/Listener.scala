package listener

import akka.NotUsed
import akka.actor.{Actor, PoisonPill}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import main.Main.system
import org.json4s.native.JsonMethods.parse
import org.json4s.{Formats, NoTypeHints, jackson}

object Listener {

  case class GetData(json: String)

  case class Message(message: String, topic: String)

}

class Listener(val number: Int) extends Actor {

  import Listener._

  val panicMessage = "{\"message\": panic}";

  import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
  import system.dispatcher

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  startStream()

  private def startStream(): Unit = {
    Http()
      .singleRequest(Get("http://localhost:4000/tweets/" + this.number))
      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
      .foreach(_.runForeach(newTweet))
  }

  //    Http()
  //      .singleRequest(Get("http://localhost:4000/tweets/2"))
  //      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
  //      .foreach(_.runForeach(println))

  def newTweet(event: ServerSentEvent): Unit = {

    if (event.data == panicMessage) {
      println("Panic!\n")
      self ! PoisonPill
    }

    val tweet = parse(event.data)

    val text = (tweet \ "message" \ "tweet" \ "text").extract[String]

    val topic = (tweet \ "message" \ "tweet" \ "user" \ "lang").extract[String]

    self ! Message(text, topic)

    //println(pretty(render(tweet)))

  }

  def receive: Receive = {
    case Message(message, topic) => {
      println("Listener" + this.number + ": " + message + " " + topic + "\n")
    }
  }

}