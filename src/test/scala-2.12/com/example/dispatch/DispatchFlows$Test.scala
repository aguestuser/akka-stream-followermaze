package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import akka.util.ByteString
import com.example.event.{BroadcastMessage, EventSourceTerminated, Subscribe}
import org.scalatest.{Matchers, WordSpec}
import DispatchFlows.{subscribeFlow, eventSourceFlow}
import com.example.serialization.SerializationFlows.crlf
import scala.concurrent.duration._


class DispatchFlows$Test extends WordSpec with Matchers {


  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private val timeout = 200 milli
  private val sleepTime = 100
  private val registerMsgBytes = ByteString(s"111$crlf")

  "DispatchFlows" should {

    "together" should {

      type TestPub = TestPublisher.Probe[ByteString]
      type TestSub = TestSubscriber.Probe[ByteString]

      case class FlowFixture(
                              alicePub: TestPub,
                              aliceSub: TestSub,
                              bobPub: TestPub,
                              bobSub: TestSub,
                              esPub: TestPub,
                              esSub: TestSub
                            )

      def fixture: FlowFixture = {
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

        val (esPub, esSub) = TestSource.probe[ByteString]
          .via(eventSourceFlow(dispatchActor))
          .toMat(TestSink.probe[ByteString])(Keep.both)
          .run()

        // signal downstream capacity for subscriptions
        esSub.request(1)

        // send subscription messages
        alicePub.sendNext(ByteString(s"111$crlf"))
        bobPub.sendNext(ByteString(s"222$crlf"))

        // ensure clients have registered before sending messages from event source
        Thread.sleep(sleepTime)

        // return fixture
        FlowFixture(alicePub, aliceSub, bobPub, bobSub, esPub, esSub)
      }

      def withFixture(test: FlowFixture => Any): Any = {
        val f = fixture
        try {
          test(f)
        }
        finally {
          f.aliceSub.request(1)
          f.bobSub.request(1)
          f.esPub.sendComplete()
        }
      }

      "broadcast a message to subscribed clients" in withFixture { f =>

        // signal downstream capacity for event source messages
        f.aliceSub.request(2)
        f.bobSub.request(2)

        // send messages out-of-order
        f.esPub.sendNext(ByteString(s"2|B$crlf"))
        f.esPub.sendNext(ByteString(s"1|B$crlf"))

        // assert they are received in order
        f.aliceSub.expectNext(ByteString(s"1|B$crlf"))
        f.aliceSub.expectNext(ByteString(s"2|B$crlf"))

        f.bobSub.expectNext(ByteString(s"1|B$crlf"))
        f.bobSub.expectNext(ByteString(s"2|B$crlf"))
      }

      "relay private messages to their recipients in correct order" in withFixture { f =>

        // signal downstream capacity for event source messages
        f.aliceSub.request(2)
        f.bobSub.request(1)

        // send messages in reverse order
        f.esPub.sendNext(ByteString(s"3|P|222|111$crlf"))
        f.esPub.sendNext(ByteString(s"2|P|111|222$crlf"))
        f.esPub.sendNext(ByteString(s"1|P|222|111$crlf"))

        // assert they are received in order
        f.aliceSub.expectNext(ByteString(s"1|P|222|111$crlf"))
        f.aliceSub.expectNext(ByteString(s"3|P|222|111$crlf"))

        f.bobSub.expectNext(ByteString(s"2|P|111|222$crlf"))
      }

      "relay follow messages to their recipients in correct order" in withFixture { f =>

        // signal downstream capacity for event source messages
        f.aliceSub.request(2)
        f.bobSub.request(1)

        // send messages in reverse order
        f.esPub.sendNext(ByteString(s"3|F|222|111$crlf"))
        f.esPub.sendNext(ByteString(s"2|F|111|222$crlf"))
        f.esPub.sendNext(ByteString(s"1|F|222|111$crlf"))

        // assert they are received in order
        f.aliceSub.expectNext(ByteString(s"1|F|222|111$crlf"))
        f.aliceSub.expectNext(ByteString(s"3|F|222|111$crlf"))

        f.bobSub.expectNext(ByteString(s"2|F|111|222$crlf"))
      }

      "not relay unfollow messages to their targets" in withFixture { f =>

        // signal downstream capacity for event source messages
        f.aliceSub.request(2)

        // send messages in reverse order
        f.esPub.sendNext(ByteString(s"3|F|222|111$crlf"))
        f.esPub.sendNext(ByteString(s"2|U|222|111$crlf"))
        f.esPub.sendNext(ByteString(s"1|P|222|111$crlf"))

        // assert they are received in order, but unfollow omited
        f.aliceSub.expectNext(ByteString(s"1|P|222|111$crlf"))
        f.aliceSub.expectNext(ByteString(s"3|F|222|111$crlf"))
      }

      "relay status update to followers" in withFixture { f =>

        // signal downstream capacity for event source messages
        f.aliceSub.request(1)
        f.bobSub.request(2)

        // send messages in reverse order
        f.esPub.sendNext(ByteString(s"5|P|111|222$crlf")) // 5. alice sends bob private message
        f.esPub.sendNext(ByteString(s"4|S|111$crlf")) // 4. alice sends second status update
        f.esPub.sendNext(ByteString(s"3|U|222|111$crlf")) // 3. bob unfollows alice
        f.esPub.sendNext(ByteString(s"2|S|111$crlf")) // 2. alice sends first status update
        f.esPub.sendNext(ByteString(s"1|F|222|111$crlf")) // 1. bob follows alice

        // assert messages received in order, but alice's second status update not received by bob
        f.aliceSub.expectNext(ByteString(s"1|F|222|111$crlf"))

        f.bobSub.expectNext(ByteString(s"2|S|111$crlf"))
        f.bobSub.expectNext(ByteString(s"5|P|111|222$crlf"))
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

        subscribeMsg.id shouldEqual "111"
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
