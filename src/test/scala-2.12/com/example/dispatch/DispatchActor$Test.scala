package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.WordSpec

class DispatchActor$Test extends WordSpec {

  import com.example.codec.MessageEventCodec.encode
  implicit val actorSystem = ActorSystem()
  val dispatchActor: ActorRef = actorSystem.actorOf(Props[DispatchActor], "TestDispatchActor")

  "DispatchActor" should {

    "handle a Subscribe message" in {
      dispatchActor ! Subscribe("123", TestProbe().ref)
    }

    "handle an Unsubscribe message" in {
      dispatchActor ! Unsubscribe("123")
    }

    "relay a Broadcast message to all subscribed clients" when {

      val broadcastMessage = BroadcastMessage(1)
      val encodedBroadcastMessage = encode(broadcastMessage)

      "no clients are subscribed" in  {
        dispatchActor ! broadcastMessage
      }

      "one client is subscribed" in {

        val actor = TestProbe()
        dispatchActor ! Subscribe("123", actor.ref)

        dispatchActor ! broadcastMessage

        actor.expectMsg(encodedBroadcastMessage)
      }

      "two clients are subscribed" in {

        val (client1, client2) = (TestProbe(), TestProbe())
        dispatchActor ! Subscribe("1", client1.ref)
        dispatchActor ! Subscribe("2", client2.ref)

        dispatchActor ! broadcastMessage

        client1.expectMsg(encodedBroadcastMessage)
        client2.expectMsg(encodedBroadcastMessage)
      }

      "a client has subscribed and unsubscribed" in {

        val (client1, client2) = (TestProbe(), TestProbe())
        dispatchActor ! Subscribe("1", client1.ref)
        dispatchActor ! Subscribe("2", client2.ref)
        dispatchActor ! Unsubscribe("1")

        dispatchActor ! broadcastMessage

        client1.expectNoMsg()
        client2.expectMsg(encodedBroadcastMessage)
      }
    }
  }
}
