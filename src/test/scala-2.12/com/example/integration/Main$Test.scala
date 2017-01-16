package com.example.integration

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.ByteString
import com.example.Main
import com.example.dispatch.DispatchFlows.crlf
import com.example.support.TcpClient
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class Main$Test extends WordSpec with Matchers {

  implicit val system = ActorSystem()
  val timeout: FiniteDuration = 200 milli
  val longTimeout: FiniteDuration = 1*timeout

  case class TcpFixture(
                         es: TestProbe,
                         esClient: ActorRef,
                         alice: TestProbe,
                         aliceClient: ActorRef,
                         bob: TestProbe,
                         bobClient: ActorRef
                       )

  def fixture: TcpFixture = {

    val eventSource = TestProbe()
    val eventSourceClient = system.actorOf(
      TcpClient.props(
        InetSocketAddress.createUnresolved("127.0.0.1", 9090),
        eventSource.ref))

    // wait for event source tcp connection before starting client connections
    eventSource.receiveOne(timeout)

    val alice = TestProbe()
    val aliceClient = system.actorOf(
      TcpClient.props(
        InetSocketAddress.createUnresolved("127.0.0.1", 9099),
        alice.ref))

    val bob = TestProbe()
    val bobClient = system.actorOf(
      TcpClient.props(
        InetSocketAddress.createUnresolved("127.0.0.1", 9099),
        bob.ref))

    // wait for client connections before subscribing clients
    alice.receiveOne(timeout)
    bob.receiveOne(timeout)

    // subscribe alice and bob
    aliceClient ! ByteString(s"123$crlf")
    bobClient ! ByteString(s"456$crlf")

    alice.receiveOne(timeout)
    bob.receiveOne(timeout)

    TcpFixture(
      eventSource,
      eventSourceClient,
      alice,
      aliceClient,
      bob,
      bobClient
    )
  }

  def withFixture(test: TcpFixture => Any): Any = {

    val f = fixture

    try {
      test(f) // run the test
    }
    finally  {
      // close event source tcp connection
      f.esClient ! "close"
      // wait for application to close user client connections
      f.alice.receiveOne(timeout)
      f.bob.receiveOne(timeout)
    }
  }

  "The program" should {

    Main.run()

    "Relay Broadcast Messages from event source to clients" when {

      "receiving one message" in withFixture { f =>

        // emit broadcast message from event source
        f.esClient ! ByteString(s"1|B$crlf")
        f.es.receiveOne(timeout)

        // expect broadcast to clients
        f.alice.receiveOne(timeout) shouldEqual ByteString(s"1|B$crlf")
        f.bob.receiveOne(timeout) shouldEqual ByteString(s"1|B$crlf")
      }

      "receiving two sequential messages out of order" in withFixture { f =>

        f.esClient ! ByteString(s"2|B$crlf")
        f.es.receiveOne(timeout)

        f.esClient ! ByteString(s"1|B$crlf")
        f.es.receiveOne(timeout)

        /**
          * TESTING NOTE:
          *
          * Because the switchboard drains the message queue faster than our TCP clients handle them,
          * when it reorders messages and then drains the queue, it *sometimes* produces a stream of bytes
          * that appear to our test TCP client as one string, despite the fact that it contains a delimiter.
          *
          * As it is outside the scope of this test to write a delimiter parser for our test TCP clients,
          * we handle the case in which two messages are concatenated into one
          * (implicitly asserting the equality of that concatenated message to the two underlying messages
          * it concatenated), and in all other cases assert the values of the sequence of messages.
          *
          * */

        val concatenatedMessages = ByteString(s"1|B${crlf}2|B$crlf")
        val messageSeq = Seq(ByteString(s"1|B$crlf"), ByteString(s"2|B$crlf"))

        val aliceMsg1 = f.alice.receiveOne(timeout)
        if (aliceMsg1 != concatenatedMessages)
          Seq(aliceMsg1, f.alice.receiveOne(timeout)) shouldEqual messageSeq

      }
    }
  }
}
