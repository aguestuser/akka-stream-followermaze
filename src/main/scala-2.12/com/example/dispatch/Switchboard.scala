package com.example.dispatch

import akka.actor.ActorRef
import akka.io.UdpConnected.Disconnect

sealed trait ConnectionEvent
case class Subscribe(id: String, subscriber: ActorRef) extends ConnectionEvent
case class Unsubscribe(id: String) extends ConnectionEvent
case object EventSourceTerminated extends ConnectionEvent

sealed trait MessageEvent
case class BroadcastMessage(seqNum: Int) extends MessageEvent
case class PrivateMessage(seqNum: Int, srcId: String, dstId: String) extends MessageEvent
case class FollowMessage(seqNum: Int, srcId: String, dstId: String) extends MessageEvent
case class UnfollowMessage(seqNum: Int, srcId: String, dstId: String) extends MessageEvent
case class StatusUpdate(seqNum: Int, srcId: String) extends MessageEvent

case class Switchboard(
                        subscribers: Map[String, ActorRef],
                        followers: Map[String, Set[ActorRef]],
                        nextMsgId: Int,
                        messages: Map[Int, MessageEvent]
                      )

object Switchboard extends DispatchLog {

  import com.example.codec.MessageEventCodec.encode

  // interface

  def empty: Switchboard = Switchboard(
    subscribers = Map.empty[String, ActorRef],
    followers = Map.empty[String, Set[ActorRef]],
    nextMsgId = 1,
    messages = Map.empty[Int, MessageEvent]
  )

  def handleConnectionEvent(e: ConnectionEvent)(sb: Switchboard): Switchboard = e match {
    case Subscribe(id, actorRef) =>
      sb.copy(subscribers = sb.subscribers + (id -> actorRef))
    case Unsubscribe(id) =>
      sb.copy(subscribers = sb.subscribers - id)
    case EventSourceTerminated =>
      sb.subscribers.foreach(_._2 ! Disconnect)
      Switchboard.empty
  }

  def handleMessage(msg: MessageEvent)(sb: Switchboard): Switchboard = msg match {
    case BroadcastMessage(seqNum) => enqueueAndDrain(seqNum, msg)(sb)
    case PrivateMessage(seqNum, _, _) => enqueueAndDrain(seqNum, msg)(sb)
    case FollowMessage(seqNum, _, _) => enqueueAndDrain(seqNum, msg)(sb)
    case UnfollowMessage(seqNum, _, _) => enqueueAndDrain(seqNum, msg)(sb)
    case StatusUpdate(seqNum, _) => enqueueAndDrain(seqNum, msg)(sb)
  }

  // helpers

  def addSubscriber(id: String, subscriber: ActorRef)(sb: Switchboard): Switchboard =
    sb.copy(subscribers = sb.subscribers + (id -> subscriber))

  def enqueueAndDrain(seqNum: Int, msg: MessageEvent): Switchboard => Switchboard =
    enqueueMessage(seqNum, msg) _ andThen drainMessageQueue

  def enqueueMessage(seqNum: Int, msg: MessageEvent)(sb: Switchboard): Switchboard =
    sb.copy(messages = sb.messages + (seqNum -> msg))

  def drainMessageQueue(sb: Switchboard): Switchboard =
    sb.messages.get(sb.nextMsgId) match {
      case None => sb
      case Some(msg) =>
        sendMessage(sb, msg)
        drainMessageQueue(
          sb.copy(
            nextMsgId = sb.nextMsgId + 1,
            messages = sb.messages - sb.nextMsgId
          )
        )
    }

  private def sendMessage(sb: Switchboard, msg: MessageEvent): Unit = {
    msg match {
      case BroadcastMessage(_) => sb.subscribers.foreach(_._2 ! encode(msg))
      case PrivateMessage(_,_,dstId) => sb.subscribers(dstId) ! encode(msg)
      case _ => sb.subscribers.foreach(_._2 ! encode(msg)) // stub
    }
    logMessageTransmission(msg)
  }
}