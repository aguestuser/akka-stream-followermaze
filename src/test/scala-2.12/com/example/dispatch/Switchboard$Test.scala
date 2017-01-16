package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem}
import akka.io.UdpConnected.Disconnect
import akka.testkit.TestProbe
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import com.example.codec.MessageEventCodec.encode

class Switchboard$Test extends WordSpec with Matchers {

  "Switchboard" should {

    import Switchboard._

    implicit val system = ActorSystem()
    val alice = TestProbe()
    val bob = TestProbe()
    val timeout = 200 milli

    "provide an empty switchboard" in {
      Switchboard.empty shouldEqual
        Switchboard(Map[String, ActorRef](), 1, Map.empty[Int, MessageEvent])
    }

    "handle a subscription" in {

      val beforeState = Switchboard(Map("1" -> alice.ref), 1, Map[Int, MessageEvent]())

      handleConnectionEvent(Subscribe("2", bob.ref))(beforeState) shouldEqual
        Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 1, Map[Int, MessageEvent]())
    }

    "handle an unsubscription" in {

      val beforeState = Switchboard(Map("1" -> alice.ref), 1, Map[Int, MessageEvent]())

      handleConnectionEvent(Unsubscribe("1"))(beforeState) shouldEqual
        Switchboard.empty
    }

    "handle an event source termination" in {

      val beforeState = Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 1, Map[Int, MessageEvent](1 -> BroadcastMessage(1)))

      handleConnectionEvent(EventSourceTerminated)(beforeState) shouldEqual
        Switchboard.empty

      alice.expectMsg(Disconnect)
      bob.expectMsg(Disconnect)
    }


    "handle a broadcast message" in {

      val beforeState = Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 1, Map(2 -> BroadcastMessage(2), 4 -> BroadcastMessage(4)))

      handleMessage(BroadcastMessage(1))(beforeState) shouldEqual
        Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 3, Map(4 -> BroadcastMessage(4)))

      alice.expectMsgAllOf(timeout, encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
      bob.expectMsgAllOf(timeout, encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
    }

    "helpers" should {

      "add a subscriber to the switchboard" when {

        "the switchboard is empty" in {
          addSubscriber("1", alice.ref)(Switchboard.empty) shouldEqual
            Switchboard(Map("1" -> alice.ref), 1, Map.empty[Int, MessageEvent])
        }

        "the switchboard is not empty" in {

          val beforeState = Switchboard(Map("1" -> alice.ref), 1, Map[Int, MessageEvent]())

          addSubscriber("2", bob.ref)(beforeState) shouldEqual
            Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 1, Map[Int, MessageEvent]())
        }
      }

      "enqueue a message" in {

        enqueueMessage(1, BroadcastMessage(1))(Switchboard.empty) shouldEqual
          Switchboard(Map[String, ActorRef](), 1, Map(1 -> BroadcastMessage(1)))
      }

      "drain the message queue" when {

        "the queue is empty" in {
          drainMessageQueue(Switchboard.empty) shouldEqual Switchboard.empty
        }

        "the next message is not in the queue" in {
          val beforeState = Switchboard(Map("1" -> alice.ref), 1, Map(2 -> BroadcastMessage(2)))

          drainMessageQueue(beforeState) shouldEqual beforeState
          alice.expectNoMsg(timeout)
        }

        "the next message is in the queue" when {

          "the next message is a broadcast message" in {

            val beforeState = Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 1, Map(1 -> BroadcastMessage(1)))

            drainMessageQueue(beforeState) shouldEqual Switchboard(beforeState.subscribers, 2, Map[Int, MessageEvent]())

            alice.expectMsg(encode(BroadcastMessage(1)))
            bob.expectMsg(encode(BroadcastMessage(1)))
          }

          "the next message is an invalid message" in {

            // TODO (requires Invalid messages to have a seq num)

          }
        }

        "the next N messages are in the queue" when {

          "the next 2 messages are broadcast messages" in {

            val beforeState = Switchboard(Map("1" -> alice.ref, "2" -> bob.ref), 1, Map(1 -> BroadcastMessage(1), 2 -> BroadcastMessage(2)))

            drainMessageQueue(beforeState) shouldEqual Switchboard(beforeState.subscribers, 3, Map[Int, MessageEvent]())

            alice.expectMsgAllOf(timeout, encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
            bob.expectMsgAllOf(timeout, encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
          }
        }
      }
    }

  }
}
