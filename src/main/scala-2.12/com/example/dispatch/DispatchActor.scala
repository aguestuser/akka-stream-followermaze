package com.example.dispatch

import akka.actor.{Actor, ActorRef}
import akka.io.UdpConnected.Disconnect

trait ConnectionEvent
case class Subscribe(id: String, subscriber: ActorRef) extends ConnectionEvent
case class Unsubscribe(id: String) extends ConnectionEvent
case object EventSourceTerminated extends ConnectionEvent

sealed trait MessageEvent
case class BroadcastMessage(seqNum: Int) extends MessageEvent
case class InvalidMessage(msg: String) extends MessageEvent

class DispatchActor extends Actor with DispatchLog {

  import com.example.codec.MessageEventCodec.encode

  var subscribers: Map[String, ActorRef] =
    Map.empty[String, ActorRef]

  override def receive: PartialFunction[Any, Unit] = {

    case Subscribe(id, subscriber) =>
      subscribers += (id -> subscriber)
      logSubscription(id, subscribers.size)

    case Unsubscribe(id) =>
      subscribers -= id
      logUnsubscription(id, subscribers.size)

    case EventSourceTerminated =>
      subscribers.foreach(_._2 ! Disconnect)
      logEventSourceTermination(subscribers.size)

    case InvalidMessage(msg) =>
      log(s"Received invalid message: $msg")

    case BroadcastMessage(seqNum) =>
      val msg = encode(BroadcastMessage(seqNum))
      subscribers.foreach(_._2 ! msg)
      logBroadcastMessage(msg, subscribers.size)
  }
}