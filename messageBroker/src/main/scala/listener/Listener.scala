package listener

import akka.{Done, NotUsed}
import akka.actor.{Actor, Kill}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import main.Main.{messageBuffers, system, topicBuffer}
import org.json4s.native.JsonMethods.parse
import org.json4s.{Formats, NoTypeHints, jackson}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.ThrottleMode
import akka.stream.alpakka.sse.scaladsl.EventSource

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Listener {

  case class GetData(json: String)

  case class Tweet(id: Int, message: String, topic: String)

  case class Message(id: Int, message: String, topic: String)

  case class OpenSource()

  var ids: AtomicInteger = new AtomicInteger(0)

  val panicMessage = "{\"message\": panic}"

}

class Listener(val number: Int) extends Actor {

  import Listener._

  import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
  import system.dispatcher

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

//  startStream()
//
//  private def startStream(): Unit = {
//    Http()
//      .singleRequest(Get("http://localhost:4000/tweets/" + this.number))
//      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
//      .foreach(_.runForeach(newTweet))
//  }

  val send: HttpRequest => Future[HttpResponse] = Http().singleRequest(_)

  var eventSource: Option[Source[ServerSentEvent, NotUsed]] =
    Option(EventSource(
      uri = Uri(s"http://localhost:4000/tweets/${this.number}"),
      send,
    ))

  val events: Future[Done] =
    eventSource
      .get
      .runForeach(newTweet)

  def newTweet(event: ServerSentEvent): Unit = {

    if (event.data == panicMessage) {
      println("Panic!\n")
      //self ! Kill
      return
    }

    val tweet = parse(event.data)

    val text = (tweet \ "message" \ "tweet" \ "text").extract[String]

    val topic = (tweet \ "message" \ "tweet" \ "user" \ "lang").extract[String]

    self ! Tweet(ids.getAndIncrement(), text, topic)

    //println(pretty(render(tweet)))

  }

  def receive: Receive = {
//    case OpenSource => {
//      println("Listener" + this.number + " opened source\n")
//      openSource()
//    }

    case Tweet(id, message, topic) => {
      var messageInTopic =
        if (messageBuffers.contains(topic)) {
          println("Listener" + this.number + " New Message in " + topic + ": " + message + "\n")
          messageBuffers(topic)
        } else {
          println("Listener" + this.number + " New Topic: " + topic + "\n")
          println("Listener" + this.number + " New Message in " + topic + ": " + message + "\n")
          topicBuffer += topic
          Array.empty[Message]
        }

      messageInTopic = messageInTopic :+ Message(id, message, topic)

      //println(messageInTopic.mkString("Array(", ", ", ")") + "\n")

      messageBuffers += (topic -> messageInTopic)
    }
  }

}