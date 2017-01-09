package com.example.dispatch

trait MessageEvent
case class Broadcast(msg: String) extends MessageEvent
