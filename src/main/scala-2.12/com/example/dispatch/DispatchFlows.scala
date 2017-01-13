package com.example.dispatch

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Framing, Sink, Source}
import akka.util.ByteString
import com.example.codec.MessageEventCodec

object DispatchFlows {

  // serialization

  // for some reason, this works with test scripts, *not* `\r\n`
  val delimiter: String = "\n"
  val delimiterBytes: ByteString = ByteString(delimiter)

  private val frameLength = 256
  private val truncation = true

  private val deserializationFlow: Flow[ByteString, String, NotUsed] =
    Flow[ByteString]
      .via(Framing.delimiter(delimiterBytes, frameLength, truncation))
      .map(_.utf8String)

  private val serializationFlow: Flow[String, ByteString, NotUsed] =
    Flow[String]
      .map(_ + delimiter)
      .map(ByteString(_))

  // subscribe

  def subscribeFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .via(registerFlow)
      .via(serializationFlow)

  private def registerFlow(implicit dispatchActor: ActorRef): Flow[String, String, NotUsed] =
    Flow[String].flatMapConcat(id =>
      Source.actorRef[String](1, OverflowStrategy.fail)
        .mapMaterializedValue(client => dispatchActor ! Subscribe(id, client))
    )

  // relay

  def eventSourceFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .map(parseEvent)
      .via(relayFlow)
      .via(serializationFlow)

  private def parseEvent(msg: String): MessageEvent = MessageEventCodec.decode(msg)

  private def relayFlow(implicit dispatchActor: ActorRef): Flow[MessageEvent, String, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.actorRef[MessageEvent](dispatchActor, EventSourceTerminated),
      // if we ever want to talk to event source, implement this stub
      Source.actorRef(1, OverflowStrategy.fail)
    )

}