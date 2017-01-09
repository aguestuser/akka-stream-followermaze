package com.example.dispatch

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Framing, Sink, Source}
import akka.util.ByteString

object DispatchFlows {

  // serialization

  private val delimiter = ByteString("\n")
  private val frameLength = 256
  private val truncation = true

  private val deserializationFlow: Flow[ByteString, String, NotUsed] =
    Flow[ByteString]
      .via(Framing.delimiter(delimiter, frameLength, truncation))
      .map(_.utf8String)

  private val serializationFlow: Flow[String, ByteString, NotUsed] =
    Flow[String]
      .map(ByteString(_))

  // subscribe

  def subscribeFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .map(parseId)
      .via(registerFlow)
      .via(serializationFlow)

  private def parseId(msg: String): String = msg.takeWhile(_ != "\n")


  // where to unsubscribe?
  private def registerFlow(implicit dispatchActor: ActorRef): Flow[String, String, NotUsed] =
    Flow[String].flatMapConcat(id =>
      Source.actorRef[String](1, OverflowStrategy.fail)
        .mapMaterializedValue(client => dispatchActor ! Subscribe(id, client))
    )
;
  // relay

  def eventSourceFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .map(parseEvent)
      .via(relayFlow)
      .via(serializationFlow)

  private def parseEvent(msg: String): MessageEvent = Broadcast(msg)

  private def relayFlow(implicit dispatchActor: ActorRef): Flow[MessageEvent, String, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.actorRef[MessageEvent](dispatchActor, EventSourceTerminated),
      Source.repeat("") // repeat so connection won't terminate
    )

}
