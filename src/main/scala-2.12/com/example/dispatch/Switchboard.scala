package com.example.dispatch

import akka.actor.ActorRef
import akka.io.UdpConnected.Disconnect
import com.example.event._

import scala.annotation.tailrec


case class Switchboard(
                        subscribers: Map[String, ActorRef],
                        followers: Map[String, Set[String]],
                        nextMsgId: Int,
                        messages: Map[Int, MessageEvent]
                      )

object Switchboard extends DispatchLog {

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

  // helpers


  def enqueueMessage(seqNum: Int, msg: MessageEvent)(sb: Switchboard): Switchboard =
    sb.copy(messages = sb.messages + (seqNum -> msg))

  @tailrec
  def drainMessageQueue(sb: Switchboard): Switchboard =
    sb.messages.get(sb.nextMsgId) match {
      case None => sb
      case Some(msg) => msg match {
        case FollowMessage(_,srcId,dstId) =>
          drainMessageQueue(
            (addFollower(srcId, dstId) _ andThen send(msg) andThen dequeueMessage)(sb)
          )
        case UnfollowMessage(_,srcId, dstId) =>
          drainMessageQueue(
            (removeFollower(srcId, dstId) _ andThen send(msg) andThen dequeueMessage)(sb)
          )
        case _ =>
          drainMessageQueue(
            (send(msg) _ andThen dequeueMessage)(sb)
          )
      }
    }

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

  private def send(msg: MessageEvent)(sb: Switchboard): Switchboard = {
    // perform sending side-effects
    msg match {
      case BroadcastMessage(_) => sb.subscribers.foreach(_._2 ! msg)
      case PrivateMessage(_,_,dstId) => sb.subscribers.get(dstId).foreach(_ ! msg)
      case FollowMessage(_,_,dstId) => sb.subscribers.get(dstId).foreach(_ ! msg)
      case UnfollowMessage(_,_,_) => ()
      case StatusUpdate(_,srcId) =>
        followersOf(sb.followers, srcId)
          .foreach(id => sb.subscribers.get(id).foreach(_ ! msg))
    }
    // perform logging side-effect
    logMessageTransmission(msg)
    // return original switchboard (for composition)
    sb
  }

  private def dequeueMessage(sb: Switchboard): Switchboard =
    sb.copy(
      nextMsgId = sb.nextMsgId + 1,
      messages = sb.messages - sb.nextMsgId
    )
}