package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.TestProbe
import org.scalatest.WordSpec

class DispatchActor$Test extends WordSpec {

  import com.example.codec.MessageEventCodec.encode
  implicit val actorSystem = ActorSystem()


  def withDispatchActor(test: ActorRef => Any): Any = {
    val dispatchActor: ActorRef = actorSystem.actorOf(Props[DispatchActor], "TestDispatchActor")
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

    "relay Broadcast messages to the switchboard" in withDispatchActor { da =>

      val broadcastMessage = BroadcastMessage(1)

      da ! broadcastMessage
    }

    "relay a Broadcast message to all subscribed clients" when {

      val broadcastMessage = BroadcastMessage(1)
      val encodedBroadcastMessage = encode(broadcastMessage)

      "no clients are subscribed" in withDispatchActor { da =>
        da ! broadcastMessage
      }

      "one client is subscribed" in withDispatchActor { da =>

        val actor = TestProbe()
        da ! Subscribe("123", actor.ref)
        Thread.sleep(100)// to ensure clients are connected before sending

        da ! broadcastMessage

        actor.expectMsg(encodedBroadcastMessage)
      }

      "two clients are subscribed" in withDispatchActor { da =>

        val (client1, client2) = (TestProbe(), TestProbe())
        da ! Subscribe("1", client1.ref)
        da ! Subscribe("2", client2.ref)
        Thread.sleep(100)

        da ! broadcastMessage

        client1.expectMsg(encodedBroadcastMessage)
        client2.expectMsg(encodedBroadcastMessage)
      }

      "a client has subscribed and unsubscribed" in withDispatchActor { da =>

        val (client1, client2) = (TestProbe(), TestProbe())
        da ! Subscribe("1", client1.ref)
        da ! Subscribe("2", client2.ref)
        da ! Unsubscribe("1")
        Thread.sleep(100)

        da ! broadcastMessage

        client1.expectNoMsg()
        client2.expectMsg(encodedBroadcastMessage)
      }
    }
  }
}
