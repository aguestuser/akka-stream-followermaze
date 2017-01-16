package com.example.dispatch

import akka.actor.Actor

class DispatchActor extends Actor with DispatchLog {

  import Switchboard.{handleConnectionEvent, handleMessage}
  var sb: Switchboard = Switchboard.empty

  override def receive: PartialFunction[Any, Unit] = {

    case Subscribe(id, actorRef) =>
      sb = handleConnectionEvent(Subscribe(id, actorRef))(sb)
      logSubscription(id, sb.subscribers.size)

    case Unsubscribe(id) =>
      sb = handleConnectionEvent(Unsubscribe(id))(sb)
      logUnsubscription(id, sb.subscribers.size)

    case EventSourceTerminated =>
      val size = sb.subscribers.size
      sb = handleConnectionEvent(EventSourceTerminated)(sb)
      logEventSourceTermination(size)

    case InvalidMessage(msg) =>
      //log(s"Received invalid message: $msg")

    case BroadcastMessage(seqNum) =>
      sb = handleMessage(BroadcastMessage(seqNum))(sb)
  }
}