package com.example.integration

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import akka.util.ByteString
import com.example.Main
import com.example.support.TcpClient
import org.scalatest.{Matchers, WordSpec}
import com.example.dispatch.DispatchFlows.delimiter

import scala.concurrent.duration._

class Main$Test extends WordSpec with Matchers {

  Main.run()

  "The program" should {


    implicit val system = ActorSystem()
    val timeout = 200 milli

    val eventSource = TestProbe()
    val eventSourceClient = system.actorOf(
      TcpClient.props(
        InetSocketAddress.createUnresolved("127.0.0.1", 9090),
        eventSource.ref))

    val alice = TestProbe()
    val aliceClient = system.actorOf(
      TcpClient.props(
        InetSocketAddress.createUnresolved("127.0.0.1", 9099),
        alice.ref))

    //aliceClient ! BroadcastMessage(1)

    val bob = TestProbe()
    val bobClient = system.actorOf(
      TcpClient.props(
        InetSocketAddress.createUnresolved("127.0.0.1", 9099),
        bob.ref))

    "Broadcast a message from the event source to all connected clients" in {

      // listen for connection confirmation before sending test data
      List(eventSource, alice, bob).foreach(_.receiveOne(timeout))

      // subscribe alice and bob
      aliceClient ! ByteString(s"123$delimiter")
      bobClient ! ByteString(s"456$delimiter")

      // wait to ensure clients have registered
      Thread.sleep(100)

      // emit broadcast message from event source
      eventSourceClient ! ByteString(s"1|B$delimiter")

      // expect broadcast
      alice.expectMsg(ByteString(s"1|B$delimiter"))
      bob.expectMsg(ByteString(s"1|B$delimiter"))
    }
  }
}
