package com.example.dispatch

import akka.actor.{Actor, ActorRef}
import akka.io.UdpConnected.Disconnect

class DispatchActor extends Actor {

  var subscribers: Map[String, ActorRef] =
    Map.empty[String, ActorRef]

  override def receive: PartialFunction[Any, Unit] = {
    case Subscribe(id, subscriber) =>
      println(s"Subscribe message: $id")
      subscribers += (id -> subscriber)
    case Unsubscribe(id) =>
      subscribers -= id
    case EventSourceTerminated =>
      subscribers.foreach(_._2 ! Disconnect)
    case Broadcast(msg) =>
      println(s"Broadcast message: $msg")
      subscribers.foreach(_._2 ! msg)
  }
}