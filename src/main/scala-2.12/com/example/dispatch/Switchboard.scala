package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem}
import akka.io.UdpConnected.Disconnect

import scalaz.State

case class Switchboard(subscribers: Map[String, ActorRef], nextMsg: Int, messages: Map[Int, MessageEvent])

sealed trait ConnectionEvent
case class Subscribe(id: String, subscriber: ActorRef) extends ConnectionEvent
case class Unsubscribe(id: String) extends ConnectionEvent
case object EventSourceTerminated extends ConnectionEvent

sealed trait MessageEvent
case class BroadcastMessage(seqNum: Int) extends MessageEvent
case class InvalidMessage(msg: String) extends MessageEvent

object Switchboard extends DispatchLog {

  import com.example.codec.MessageEventCodec.encode

  // interface

  def empty: Switchboard = Switchboard(
    subscribers = Map.empty[String, ActorRef],
    nextMsg = 1,
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
    case BroadcastMessage(seqNum) =>
      enqueueAndDrain(seqNum, msg)(sb)
    case _ => sb
  }

  // helpers

  def addSubscriber(id: String, subscriber: ActorRef)(sb: Switchboard): Switchboard =
    sb.copy(subscribers = sb.subscribers + (id -> subscriber))

  def enqueueAndDrain(seqNum: Int, msg: MessageEvent): Switchboard => Switchboard =
    enqueueMessage(seqNum, msg) _ andThen drainMessageQueue

  def enqueueMessage(seqNum: Int, msg: MessageEvent)(sb: Switchboard): Switchboard =
    sb.copy(messages = sb.messages + (seqNum -> msg))

  def drainMessageQueue(sb: Switchboard): Switchboard =
    sb.messages.get(sb.nextMsg) match {
      case None => sb
      case Some(msg) =>
        msg match {
          case BroadcastMessage(_) =>
            sb.subscribers.foreach(_._2 ! encode(msg))
            logMessage(msg, sb)
            drainMessageQueue(Switchboard(sb.subscribers, sb.nextMsg + 1, sb.messages - sb.nextMsg))
          case _ => sb
        }
    }
}