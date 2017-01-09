package com.example.dispatch

import akka.actor.ActorRef

trait ConnectionEvent
case class Subscribe(id: String, subscriber: ActorRef) extends ConnectionEvent
case class Unsubscribe(id: String) extends ConnectionEvent
case object EventSourceTerminated extends ConnectionEvent
