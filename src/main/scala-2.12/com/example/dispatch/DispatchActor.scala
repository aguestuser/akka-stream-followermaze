package com.example.dispatch

import akka.actor.Actor

class DispatchActor extends Actor with DispatchLog {

  import Switchboard.{handleConnectionEvent, handleMessage}
  var sb: Switchboard = Switchboard.empty

  override def receive: PartialFunction[Any, Unit] = {

    // connection events

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

    // message events

    case BroadcastMessage(seqNum) =>
      sb = handleMessage(BroadcastMessage(seqNum))(sb)

    case PrivateMessage(seqNum, srcId, dstId) =>
      sb = handleMessage(PrivateMessage(seqNum, srcId, dstId))(sb)

    case FollowMessage(seqNum, srcId, dstId) =>
      sb = handleMessage(FollowMessage(seqNum, srcId, dstId))(sb)

    case UnfollowMessage(seqNum, srcId, dstId) =>
      sb = handleMessage(UnfollowMessage(seqNum, srcId, dstId))(sb)

    case StatusUpdate(seqNum, srcId) =>
      sb = handleMessage(StatusUpdate(seqNum, srcId))(sb)

  }
}