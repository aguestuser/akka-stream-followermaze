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
    aliceClient ! ByteString(s"111$crlf")
    bobClient ! ByteString(s"222$crlf")

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

  def didReceiveMessages(client: TestProbe, msg1: String, msg2: String): Boolean = {

    /**
      * TESTING NOTE:
      *
      * Because the switchboard drains the message queue faster than our TCP clients handle them,
      * when it reorders messages and then drains the queue, it *sometimes* produces a stream of bytes
      * containing two messages that appear to our test TCP client to be one message,
      * despite the fact that the string transmitted over the wire contains a CRLF delimiter.
      *
      * As it is outside the scope of this assignment to write a delimiter parser for our test TCP clients,
      * we write a custom matcher that will return true in both the case (1) when messages have been concatenated
      * into one string and (2) in which they are transmitted as two separate strings.
      *
      * */

    val clientMsg1 = client.receiveOne(timeout)
    clientMsg1 == ByteString(s"$msg1$msg2") ||
      Seq(clientMsg1, client.receiveOne(timeout)) == Seq(ByteString(msg1), ByteString(msg2))

  }

  "The program" should {

    Main.run()

    "Relay Broadcast Messages to all subscribed clients" when {

      "receiving one message" in withFixture { f =>

        // emit broadcast message from event source
        f.esClient ! ByteString(s"1|B$crlf")
        f.es.receiveOne(timeout)

        // expect broadcast to clients
        f.alice.receiveOne(timeout) shouldEqual ByteString(s"1|B$crlf")
        f.bob.receiveOne(timeout) shouldEqual ByteString(s"1|B$crlf")
      }

      "receiving two sequential messages out of order" in withFixture { f =>

        // emit broadcast messages from event source
        f.esClient ! ByteString(s"2|B$crlf")
        f.es.receiveOne(timeout)

        f.esClient ! ByteString(s"1|B$crlf")
        f.es.receiveOne(timeout)

        // assert they were received in order
        didReceiveMessages(f.alice, s"1|B$crlf", s"2|B$crlf") shouldBe true
        didReceiveMessages(f.bob, s"1|B$crlf", s"2|B$crlf") shouldBe true
      }
    }

    "Transmit Private Messages to their intended recipients" when {

      "receiving one message" in withFixture { f =>

        f.esClient ! ByteString(s"1|P|222|111$crlf")
        f.es.receiveOne(timeout)

        f.alice.expectMsg(timeout, ByteString(s"1|P|222|111$crlf"))
        f.bob.expectNoMsg(timeout)
      }

      "receiving multiple out-of-order messages" in withFixture { f =>

        // emit private messages from event source out-of-order
        f.esClient ! ByteString(s"3|P|222|111$crlf")
        f.es.receiveOne(timeout)

        f.esClient ! ByteString(s"2|P|111|222$crlf")
        f.es.receiveOne(timeout)

        f.esClient ! ByteString(s"1|P|222|111$crlf")
        f.es.receiveOne(timeout)

        // assert they were received in order
        didReceiveMessages(f.alice, s"1|P|222|111$crlf", s"3|P|222|111$crlf") shouldBe true
        f.bob.receiveOne(timeout) shouldEqual ByteString(s"2|P|111|222$crlf")
      }
    }

    "Transmit Follow Messages to their intended recipients" when {

      "receiving one message" in withFixture { f =>

        f.esClient ! ByteString(s"1|F|222|111$crlf")
        f.es.receiveOne(timeout)

        f.alice.expectMsg(timeout, ByteString(s"1|F|222|111$crlf"))
        f.bob.expectNoMsg(timeout)
      }

      "receiving multiple out-of-order messages" in withFixture { f =>

        // emit private messages from event source out-of-order
        f.esClient ! ByteString(s"3|F|222|111$crlf")
        f.es.receiveOne(timeout)

        f.esClient ! ByteString(s"2|F|111|222$crlf")
        f.es.receiveOne(timeout)

        f.esClient ! ByteString(s"1|F|222|111$crlf")
        f.es.receiveOne(timeout)

        // assert they were received in order
        didReceiveMessages(f.alice, s"1|F|222|111$crlf", s"3|F|222|111$crlf") shouldBe true
        f.bob.receiveOne(timeout) shouldEqual ByteString(s"2|F|111|222$crlf")
      }
    }

    "Correctly handle a combination of message types sent out-of-order" in  withFixture { f =>

      // emit messages from event source out-of-order
      f.esClient ! ByteString(s"3|F|111|222$crlf")
      f.es.receiveOne(timeout)

      f.esClient ! ByteString(s"2|P|222|111$crlf")
      f.es.receiveOne(timeout)

      f.esClient ! ByteString(s"1|B$crlf")
      f.es.receiveOne(timeout)

      // assert they were received in order
      didReceiveMessages(f.alice, s"1|B$crlf", s"2|P|222|111$crlf") shouldBe true
      didReceiveMessages(f.bob, s"1|B$crlf", s"3|F|111|222$crlf") shouldBe true
    }
  }
}
