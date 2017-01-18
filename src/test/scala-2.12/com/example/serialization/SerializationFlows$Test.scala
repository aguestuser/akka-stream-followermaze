package com.example.serialization
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.example.event.{BroadcastMessage, MessageEvent}
import org.scalatest.{Matchers, WordSpec}

class SerializationFlows$Test extends WordSpec with Matchers {

  import SerializationFlows._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "crlf" should {

    "be a newline character" in {
      crlf shouldEqual "\n" // regression test!
    }
  }

  "deserializationFlow" should {

    "transform a stream of Bytestrings to a stream of Strings" in {

      val (pub, sub) = TestSource.probe[ByteString]
        .via(deserializationFlow)
        .toMat(TestSink.probe[String])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext(ByteString(s"foo$crlf"))

      sub.expectNext("foo")
    }
  }

  "serializationFlow" should {

    "transform a stream of Strings into a stream of ByteStrings" in {

      val (pub, sub) = TestSource.probe[String]
        .via(serializationFlow)
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext("foo")

      sub.expectNext(ByteString(s"foo$crlf"))
    }
  }

  "eventSourceDeserializationFlow" should {

    "transform a stream of ByteStrings to a stream of MessageEvents" in {

      val (pub, sub) = TestSource.probe[ByteString]
        .via(eventSourceDeserializationFlow)
        .toMat(TestSink.probe[MessageEvent])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext(ByteString(s"1|B$crlf"))

      sub.expectNext(BroadcastMessage(1))
    }

  }

  "clientSerializationFlow" should {

    "transform a stream of MessageEvents into a stream of ByteStrings" in {

      val (pub, sub) = TestSource.probe[MessageEvent]
        .via(clientSerializationFlow)
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext(BroadcastMessage(1))

      sub.expectNext(ByteString(s"1|B$crlf"))
    }
  }
}