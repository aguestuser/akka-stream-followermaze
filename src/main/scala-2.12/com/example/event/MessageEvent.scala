package com.example.event

sealed trait MessageEvent
case class BroadcastMessage(seqNum: Int) extends MessageEvent
case class PrivateMessage(seqNum: Int, srcId: String, dstId: String) extends MessageEvent
case class FollowMessage(seqNum: Int, srcId: String, dstId: String) extends MessageEvent
case class UnfollowMessage(seqNum: Int, srcId: String, dstId: String) extends MessageEvent
case class StatusUpdate(seqNum: Int, srcId: String) extends MessageEvent