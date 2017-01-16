package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}

class DispatchFlows$Test extends WordSpec with Matchers {

  import DispatchFlows.{subscribeFlow, eventSourceFlow, crlf}
  import com.example.codec.MessageEventCodec.encode
  import scala.concurrent.duration._

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private val timeout = 200 milli

  private val registerMsgBytes = ByteString(s"123$crlf")
  private val broadcastMsgBytes = ByteString(s"1|B$crlf")
  private val broadcastMsg = BroadcastMessage(1)

  "DispatchFlows" should {

    "together" should {

      // materialize client and event source flows
      val dispatchActor: ActorRef = system.actorOf(Props[DispatchActor])

      val (alicePub, aliceSub) = TestSource.probe[ByteString]
        .via(subscribeFlow(dispatchActor))
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      val (bobPub, bobSub) = TestSource.probe[ByteString]
        .via(subscribeFlow(dispatchActor))
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      val (eventSourcePub, eventSourceSub) = TestSource.probe[ByteString]
        .via(eventSourceFlow(dispatchActor))
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      "broadcast messages subscribed clients via the Dispatch Actor" in {

        // create downstream pull
        aliceSub.request(2)
        bobSub.request(2)
        eventSourceSub.request(1)

        // pass strings through stream
        alicePub.sendNext(ByteString(s"123$crlf"))
        bobPub.sendNext(ByteString(s"456$crlf"))

        // ensure clients have registered before sending test messages
        Thread.sleep(100)

        // send messages out-of-order
        eventSourcePub.sendNext(ByteString(s"2|B$crlf"))
        eventSourcePub.sendNext(ByteString(s"1|B$crlf"))

        // assert they are received in order
        aliceSub.expectNext(ByteString(s"1|B$crlf"))
        aliceSub.expectNext(ByteString(s"2|B$crlf"))

        bobSub.expectNext(ByteString(s"1|B$crlf"))
        bobSub.expectNext(ByteString(s"2|B$crlf"))
      }
    }


    "subscribeFlow" should {

      val dispatchActor = TestProbe()

      val (pub, sub) = TestSource.probe[ByteString]
        .via(subscribeFlow(dispatchActor.ref))
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      "register an actor wrapping a client socket to receive messages from the Dispatch Actor" in {

        sub.request(1)
        pub.sendNext(registerMsgBytes)

        // NOTE: b/c we can't get a reference to the client actor created in the flow,
        // we destructure the Subscribe message to assert on the actor's type
        val subscribeMsg = dispatchActor.receiveOne(timeout).asInstanceOf[Subscribe]

        subscribeMsg.id shouldEqual "123"
        subscribeMsg.subscriber shouldBe an [ActorRef]
      }

      "unregister the actor wrapping a client socket from Dispatch Actor messages when the socket is closed" in {
        // TODO
      }
    }

    "eventSourceFlow" should {

      val dispatchActor = TestProbe()

      val (pub, sub) = TestSource.probe[ByteString]
        .via(eventSourceFlow(dispatchActor.ref))
        .toMat(TestSink.probe[ByteString])(Keep.both)
        .run()

      "route messages to the Dispatch Actor" in {

        sub.request(1)
        pub.sendNext(ByteString(s"1|B$crlf"))

        dispatchActor.expectMsg(BroadcastMessage(1))
      }

      "send a termination message to the Dispatch actor when event source socket is closed" in {

        sub.request(1)
        pub.sendComplete()

        dispatchActor.expectMsg(EventSourceTerminated)
      }
    }
  }
}
