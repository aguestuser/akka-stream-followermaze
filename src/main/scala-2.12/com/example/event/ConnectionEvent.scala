package com.example.event

import akka.actor.ActorRef

sealed trait ConnectionEvent
case class Subscribe(id: String, subscriber: ActorRef) extends ConnectionEvent
case class Unsubscribe(id: String) extends ConnectionEvent
case object EventSourceTerminated extends ConnectionEvent