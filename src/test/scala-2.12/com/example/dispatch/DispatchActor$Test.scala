package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.TestProbe
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.duration._

class DispatchActor$Test extends WordSpec with Matchers {

  import com.example.codec.MessageEventCodec.encode
  implicit val actorSystem = ActorSystem()
  val timeout: FiniteDuration = 100 millis
  val sleepTime: Int = 25

  def withDispatchActor(test: ActorRef => Any): Any = {
    val dispatchActor: ActorRef = actorSystem.actorOf(Props[DispatchActor])
    try {
      test(dispatchActor)
    } finally {
      dispatchActor ! PoisonPill
    }
  }

  "DispatchActor" should {

    "handle a Subscribe message" in withDispatchActor { da =>
      da ! Subscribe("123", TestProbe().ref)
    }

    "handle an Unsubscribe message" in withDispatchActor { da =>
      da ! Unsubscribe("123")
    }

    "relay a Broadcast message to all subscribed clients" when {

      val broadcastMessage = BroadcastMessage(1)
      val encodedBroadcastMessage = encode(broadcastMessage)
      val encodedBroadcastMessages = Seq(
        encode(BroadcastMessage(1)),
        encode(BroadcastMessage(2))
      )

      "no clients are subscribed" in withDispatchActor { da =>
        da ! broadcastMessage
      }

      "one client is subscribed" in withDispatchActor { da =>

        val actor = TestProbe()
        da ! Subscribe("123", actor.ref)
        Thread.sleep(sleepTime)// to ensure clients are connected before sending

        da ! broadcastMessage

        actor.expectMsg(encodedBroadcastMessage)
      }

      "two clients are subscribed" in withDispatchActor { da =>

        val (alice, bob) = (TestProbe(), TestProbe())
        da ! Subscribe("1", alice.ref)
        da ! Subscribe("2", bob.ref)
        Thread.sleep(sleepTime)

        da ! broadcastMessage

        alice.expectMsg(encodedBroadcastMessage)
        bob.expectMsg(encodedBroadcastMessage)
      }

      "a client has subscribed and unsubscribed" in withDispatchActor { da =>

        val (alice, bob) = (TestProbe(), TestProbe())
        da ! Subscribe("1", alice.ref)
        da ! Subscribe("2", bob.ref)
        da ! Unsubscribe("1")
        Thread.sleep(sleepTime)

        da ! broadcastMessage

        alice.expectNoMsg(timeout)
        bob.expectMsg(encodedBroadcastMessage)
      }

      "two messages are received out of order" in withDispatchActor { da =>

        val (alice, bob) = (TestProbe(), TestProbe())
        da ! Subscribe("1", alice.ref)
        da ! Subscribe("2", bob.ref)
        Thread.sleep(sleepTime)

        da ! BroadcastMessage(2)
        da ! BroadcastMessage(1)

        alice.receiveN(2) shouldEqual encodedBroadcastMessages
        bob.receiveN(2) shouldEqual encodedBroadcastMessages
      }
    }

    "relay a Private Messages to their recipients in correct order" in withDispatchActor { da =>

      val (alice, bob) = (TestProbe(), TestProbe())
      da ! Subscribe("1", alice.ref)
      da ! Subscribe("2", bob.ref)
      Thread.sleep(sleepTime)

      da ! PrivateMessage(2, "3", "1")
      da ! PrivateMessage(1, "2", "1")

      alice.receiveN(2, timeout) shouldEqual Seq(
        encode(PrivateMessage(1, "2", "1")),
        encode(PrivateMessage(2, "3", "1"))
      )
      bob.expectNoMsg(timeout)
    }

    "relay Follow Messages to their recipients in correct order" in withDispatchActor { da =>

      val (alice, bob) = (TestProbe(), TestProbe())
      da ! Subscribe("1", alice.ref)
      da ! Subscribe("2", bob.ref)
      Thread.sleep(sleepTime)

      da ! FollowMessage(2, "3", "1")
      da ! FollowMessage(1, "2", "1")

      alice.receiveN(2, timeout) shouldEqual Seq(
        encode(FollowMessage(1, "2", "1")),
        encode(FollowMessage(2, "3", "1"))
      )
      bob.expectNoMsg(timeout)

    }

    "not relay Unfollow Messages to their targets" in withDispatchActor { da =>

      val (alice, bob) = (TestProbe(), TestProbe())
      da ! Subscribe("1", alice.ref)
      da ! Subscribe("2", bob.ref)
      Thread.sleep(sleepTime)

      da ! UnfollowMessage(2, "1", "2")
      da ! UnfollowMessage(1, "2", "1")

      alice.expectNoMsg(timeout)
      bob.expectNoMsg(timeout)

    }

    "Relay Status Updates to followers" in withDispatchActor { da =>

      val (alice, bob) = (TestProbe(), TestProbe())
      da ! Subscribe("1", alice.ref)
      da ! Subscribe("2", bob.ref)
      Thread.sleep(sleepTime)

      // send messages in reverse order
      da ! StatusUpdate(4, "111") // 4. alice sends second status update
      da ! UnfollowMessage(3, "2", "1") // 3. bob unfollows alice
      da ! StatusUpdate(2, "1") // 2. alice sends first status update
      da ! FollowMessage(1, "2", "1") // 1. bob follows alice

      alice.expectMsgAllOf(timeout, encode(FollowMessage(1, "2", "1"))) // don't receive unfollow
      bob.expectMsgAllOf(timeout, encode(StatusUpdate(2, "1"))) // don't receive second update

    }
  }
}
