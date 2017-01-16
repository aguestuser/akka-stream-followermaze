package com.example.dispatch

trait DispatchLog {

  import com.example.codec.MessageEventCodec.encode

  // main function

  def log(msg: String): Unit = println(s"+ $msg") // testing seam

  // connection events

  def logSubscription(id: String, count: Int): Unit =
    log(subscriptionNotificationOf(id, count))

  private def subscriptionNotificationOf(id: String, count: Int): String =
    s"Subscribed client with id: $id. $count clients now subscribed."

  def logUnsubscription(id: String, count: Int): Unit =
    log(unsubscriptionNotificationOf(id, count))

  private def unsubscriptionNotificationOf(id: String, count: Int): String =
    s"Unubscribed client with id: $id. $count clients now subscribed."

  def logEventSourceTermination(count: Int): Unit =
    log(eventSourceTerminationNoticeOf(count))

  private def eventSourceTerminationNoticeOf(count: Int): String =
    s"Event Source connection lost. Disconnected $count clients."

  // message events

  def logMessage(msg: MessageEvent, sb: Switchboard): Unit = msg match {
    case BroadcastMessage(_) =>
      log(broadcastNotificationOf(encode(msg), sb.subscribers.size))
  }

  def logBroadcastMessage(msg: String, count: Int): Unit =
    log(broadcastNotificationOf(msg, count))

  private def broadcastNotificationOf(msg: String, count: Int): String =
    s"Broadcast `$msg` to $count clients."
}
