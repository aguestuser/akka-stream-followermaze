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
                        followers: Map[String, Set[String]],
                        nextMsgId: Int,
                        messages: Map[Int, MessageEvent]
                      )

object Switchboard extends DispatchLog {

  import com.example.codec.MessageEventCodec.encode

  // interface

  def empty: Switchboard = Switchboard(
    subscribers = Map.empty[String, ActorRef],
    followers = Map.empty[String, Set[String]],
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

  def handleMessage(seqNum: Int, msg: MessageEvent)(sb: Switchboard): Switchboard =
    (enqueueMessage(seqNum, msg) _ andThen drainMessageQueue)(sb)

  // connection helpers

  def addSubscriber(id: String, subscriber: ActorRef)(sb: Switchboard): Switchboard =
    sb.copy(subscribers = sb.subscribers + (id -> subscriber))

  // message helpers

  def addFollower(srcId: String, dstId: String)(sb: Switchboard): Switchboard =
    sb.copy(
      followers = sb.followers + (dstId -> (followersOf(sb.followers, dstId) + srcId))
    )

  def removeFollower(srcId: String, dstId: String)(sb: Switchboard): Switchboard =
    sb.copy(
      followers = sb.followers + (dstId -> (followersOf(sb.followers, dstId) - srcId))
    )

  private def followersOf(followers: Map[String, Set[String]], id: String) =
    followers.getOrElse(id, Set.empty[String])

  def enqueueMessage(seqNum: Int, msg: MessageEvent)(sb: Switchboard): Switchboard =
    sb.copy(messages = sb.messages + (seqNum -> msg))

  def drainMessageQueue(sb: Switchboard): Switchboard =
    sb.messages.get(sb.nextMsgId) match {
      case None => sb
      case Some(msg) => msg match {
        case FollowMessage(_,srcId,dstId) =>
          (addFollower(srcId, dstId) _ andThen send(msg))(sb)
        case UnfollowMessage(_,srcId, dstId) =>
          (removeFollower(srcId, dstId) _ andThen send(msg))(sb)
        case _ =>
          send(msg)(sb)
      }
    }

  private def send(msg: MessageEvent)(sb: Switchboard): Switchboard = {
    // perform sending side-effects
    msg match {
      case BroadcastMessage(_) => sb.subscribers.foreach(_._2 ! encode(msg))
      case PrivateMessage(_,_,dstId) => sb.subscribers.get(dstId).foreach(_ ! encode(msg))
      case FollowMessage(_,_,dstId) => sb.subscribers.get(dstId).foreach(_ ! encode(msg))
      case UnfollowMessage(_,_,_) => ()
      case StatusUpdate(_,srcId) =>
        followersOf(sb.followers, srcId)
          .foreach(id => sb.subscribers.get(id).foreach(_ ! encode(msg)))
    }
    // perform logging side-effect
    logMessageTransmission(msg)
    // drain queue of updated switchboard
    drainMessageQueue( sb.copy(nextMsgId = sb.nextMsgId + 1, messages = sb.messages - sb.nextMsgId))
  }

  private def maybeSend(maybeClient: Option[ActorRef], msg: MessageEvent): Unit =
    maybeClient match {
      case Some(actorRef) => actorRef ! encode(msg)
      case _ => ()
    }
}