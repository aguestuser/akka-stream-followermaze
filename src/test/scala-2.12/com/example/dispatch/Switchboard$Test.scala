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
        Switchboard(
          Map[String, ActorRef](),
          Map.empty[String, Set[ActorRef]],
          1,
          Map.empty[Int, MessageEvent]
        )
    }

    "handle a subscription" in {

      val beforeState = Switchboard(
        Map("1" -> alice.ref),
        Map.empty[String, Set[ActorRef]],
        1,
        Map[Int, MessageEvent]()
      )

      handleConnectionEvent(Subscribe("2", bob.ref))(beforeState) shouldEqual
        Switchboard(
          Map("1" -> alice.ref, "2" -> bob.ref),
          beforeState.followers,
          beforeState.nextMsgId,
          beforeState.messages
        )
    }

    "handle an unsubscription" in {

      val beforeState = Switchboard(
        Map("1" -> alice.ref),
        Map.empty[String, Set[ActorRef]],
        1,
        Map.empty[Int, MessageEvent]
      )

      handleConnectionEvent(Unsubscribe("1"))(beforeState) shouldEqual
        Switchboard.empty
    }

    "handle an event source termination" in {

      val beforeState = Switchboard(
        Map("1" -> alice.ref, "2" -> bob.ref),
        Map.empty[String, Set[ActorRef]],
        1,
        Map[Int, MessageEvent](1 -> BroadcastMessage(1))
      )

      handleConnectionEvent(EventSourceTerminated)(beforeState) shouldEqual
        Switchboard.empty

      alice.expectMsg(Disconnect)
      bob.expectMsg(Disconnect)
    }


    "handle a broadcast message" when {

      val beforeState = Switchboard(
        Map("1" -> alice.ref, "2" -> bob.ref),
        Map.empty[String, Set[ActorRef]],
        1,
        Map(2 -> BroadcastMessage(2), 4 -> BroadcastMessage(4))
      )

      "the message is next in sequence: enqueue and send (all in sequence)" in {

        handleMessage(BroadcastMessage(1))(beforeState) shouldEqual
          Switchboard(
            beforeState.subscribers,
            beforeState.followers,
            3,
            Map(4 -> BroadcastMessage(4))
          )

        alice.expectMsgAllOf(timeout, encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
        bob.expectMsgAllOf(timeout, encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
      }

      "the message is not next in sequence: only enqueue" in {

        handleMessage(BroadcastMessage(3))(beforeState) shouldEqual
          Switchboard(
            beforeState.subscribers,
            beforeState.followers,
            beforeState.nextMsgId,
            Map(2 -> BroadcastMessage(2), 3 -> BroadcastMessage(3), 4 -> BroadcastMessage(4))
          )

        alice.expectNoMsg(timeout)
        bob.expectNoMsg(timeout)
      }
    }

    "handle a private message" in {

      // only test scenario in which message is next in sequence
      // as other scenarios are adequately covered by broadcast tests

      val beforeState = Switchboard(
        Map("1" -> alice.ref, "2" -> bob.ref),
        Map.empty[String, Set[ActorRef]],
        1,
        Map.empty[Int, MessageEvent]
      )

      handleMessage(PrivateMessage(1, "1", "2"))(beforeState) shouldEqual Switchboard(
        beforeState.subscribers,
        beforeState.followers,
        2,
        Map.empty[Int, MessageEvent]
      )

      alice.expectNoMsg(timeout)
      bob.expectMsg(encode(PrivateMessage(1, "1", "2")))
    }

    "helpers" should {

      "add a subscriber to the switchboard" when {

        "the switchboard is empty" in {

          val beforeState = Switchboard.empty

          addSubscriber("1", alice.ref)(Switchboard.empty) shouldEqual
            Switchboard(
              Map("1" -> alice.ref),
              beforeState.followers,
              beforeState.nextMsgId,
              beforeState.messages
            )
        }

        "the switchboard is not empty" in {

          val beforeState = Switchboard(
            Map("1" -> alice.ref),
            Map.empty[String, Set[ActorRef]],
            1,
            Map.empty[Int, MessageEvent]
          )

          addSubscriber("2", bob.ref)(beforeState) shouldEqual
            Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              beforeState.followers,
              beforeState.nextMsgId,
              beforeState.messages
            )
        }
      }

      "enqueue a message" in {

        val beforeState = Switchboard.empty

        enqueueMessage(1, BroadcastMessage(1))(beforeState) shouldEqual
          Switchboard(
            beforeState.subscribers,
            beforeState.followers,
            beforeState.nextMsgId,
            Map(1 -> BroadcastMessage(1))
          )
      }

      "drain the message queue" when {

        "the queue is empty" in {
          drainMessageQueue(Switchboard.empty) shouldEqual Switchboard.empty
        }

        "the next message is not in the queue" in {
          val beforeState = Switchboard(
            Map("1" -> alice.ref),
            Map.empty[String, Set[ActorRef]],
            1,
            Map(2 -> BroadcastMessage(2))
          )

          drainMessageQueue(beforeState) shouldEqual beforeState
          alice.expectNoMsg(timeout)
        }

        "the next message is in the queue" when {

          "the next message is a broadcast message" in {

            val beforeState = Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              Map.empty[String, Set[ActorRef]],
              1,
              Map(1 -> BroadcastMessage(1))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(
              beforeState.subscribers,
              beforeState.followers,
              2,
              Map.empty[Int, MessageEvent]
            )

            alice.expectMsg(encode(BroadcastMessage(1)))
            bob.expectMsg(encode(BroadcastMessage(1)))
          }

          "the next message is a private message" in {
            val beforeState = Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              Map.empty[String, Set[ActorRef]],
              1,
              Map(1 -> PrivateMessage(1, "1", "2"))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(
              beforeState.subscribers,
              beforeState.followers,
              2,
              Map[Int, MessageEvent]()
            )

            alice.expectNoMsg(timeout)
            bob.expectMsg(encode(PrivateMessage(1, "1", "2")))

          }
        }

        "the next N messages are in the queue" when {

          "all messages are broadcast messages" in {

            val beforeState = Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              Map.empty[String, Set[ActorRef]],
              1,
              Map(1 -> BroadcastMessage(1),2 -> BroadcastMessage(2))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(
              beforeState.subscribers,
              beforeState.followers,
              3,
              Map.empty[Int, MessageEvent]
            )

            // enforce order of messages
            alice.receiveN(2) shouldEqual Seq(encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
            bob.receiveN(2) shouldEqual Seq(encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
          }

          "all messages are private messages" in {

            val beforeState = Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              Map.empty[String, Set[ActorRef]],
              1,
              Map(1 -> PrivateMessage(1, "1", "2"), 2 -> PrivateMessage(2, "2", "1"))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(
              beforeState.subscribers,
              beforeState.followers,
              3,
              Map.empty[Int, MessageEvent]
            )

            alice.expectMsg(encode(PrivateMessage(2, "2", "1")))
            bob.expectMsg(encode(PrivateMessage(1, "1", "2")))
          }

          "the messages are a mix of broadcast and private messages" in {

            val beforeState = Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              Map.empty[String, Set[ActorRef]],
              1,
              Map(1 -> BroadcastMessage(1), 2 -> PrivateMessage(2, "2", "1"))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(
              beforeState.subscribers,
              beforeState.followers,
              3,
              Map.empty[Int, MessageEvent]
            )

            // enforce order of alice's messages
            alice.receiveN(2) shouldEqual Seq(encode(BroadcastMessage(1)), encode(PrivateMessage(2, "2", "1")))
            bob.expectMsg(encode(BroadcastMessage(1)))
          }
        }

        "the next N messages and an out-of-order message are in the queue" when {

          "all messages are broadcast messages" in {

            val beforeState = Switchboard(
              Map("1" -> alice.ref, "2" -> bob.ref),
              Map.empty[String, Set[ActorRef]],
              1,
              Map(1 -> BroadcastMessage(1), 2 -> BroadcastMessage(2), 4 -> BroadcastMessage(4))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(
              beforeState.subscribers,
              beforeState.followers,
              3,
              Map(4 -> BroadcastMessage(4))
            )

            alice.receiveN(2) shouldEqual Seq(encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
            bob.receiveN(2) shouldEqual Seq(encode(BroadcastMessage(1)), encode(BroadcastMessage(2)))
          }
        }
      }
    }
  }
}
