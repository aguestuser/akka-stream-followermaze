package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem}

import scalaz.State

case class Switchboard(subscribers: Map[String, ActorRef], nextMsg: Int, messages: Map[Int, DispatchEvent])

import State._

object Switchboard {

  // interface

  def empty: Switchboard = Switchboard(subscribers = Map.empty[String, ActorRef], nextMsg = 1, messages = Map.empty[Int, DispatchEvent])

  def handleMessage(msg: MessageEvent): State[Switchboard, Unit] = msg match {
    case BroadcastMessage(seqNum) => for {
      _ <- modify { enqueueMessage(seqNum, msg) }
      _ <- modify { drainMessageQueue }
      sb <- get[Switchboard]
    } yield sb
    case _ => state(())
  }

  // helpers

  def addSubscriber(id: String, subscriber: ActorRef)(sb: Switchboard): Switchboard =
    sb.copy(subscribers = sb.subscribers + (id -> subscriber))

  def enqueueMessage(seqNum: Int, msg: MessageEvent)(sb: Switchboard): Switchboard =
    sb.copy(messages = sb.messages + (seqNum -> msg))

  def drainMessageQueue(sb: Switchboard): Switchboard =
    sb.messages.get(sb.nextMsg) match {
      case None => sb
      case Some(msg) =>
        msg match {
          case BroadcastMessage(_) =>
            sb.subscribers.foreach(_._2 ! msg)
            drainMessageQueue(Switchboard(sb.subscribers, sb.nextMsg + 1, sb.messages - sb.nextMsg))
          case _ => sb
        }
    }
}