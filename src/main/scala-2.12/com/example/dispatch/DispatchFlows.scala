package com.example.dispatch

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Framing, Sink, Source}
import akka.util.ByteString
import com.example.codec.MessageEventCodec
import com.example.event.{EventSourceTerminated, MessageEvent, Subscribe}

object DispatchFlows {

  val subscriptionBufferSize: Int = 100

  // serialization (consider extracting to separate file)
  val crlf: String = "\n" // for some reason `\n` *not* `\r\n`, works with test scripts
  private val crlfBytes = ByteString(crlf)
  private val maxFrameLength = 1024
  private val truncation = true

  private val deserializationFlow: Flow[ByteString, String, NotUsed] =
    Flow[ByteString]
      .via(Framing.delimiter(crlfBytes, maxFrameLength, truncation))
      .map(_.utf8String)

  private val serializationFlow: Flow[String, ByteString, NotUsed] =
    Flow[String]
      .map(_ + crlf)
      .map(ByteString(_))

  // client subscription

  def subscribeFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .via(registerFlow)
      .via(serializationFlow)

  private def registerFlow(implicit dispatchActor: ActorRef): Flow[String, String, NotUsed] =
    Flow[String].flatMapConcat(id =>
      Source.actorRef[String](subscriptionBufferSize, OverflowStrategy.fail)
        .mapMaterializedValue(client => dispatchActor ! Subscribe(id, client))
    )

  // event source transmission

  def eventSourceFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .map(parseEvent)
      .via(relayFlow)
      .via(serializationFlow)

  private def parseEvent(msg: String): MessageEvent = MessageEventCodec.decode(msg)

  private def relayFlow(implicit dispatchActor: ActorRef): Flow[MessageEvent, String, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.actorRef[MessageEvent](dispatchActor, EventSourceTerminated),
      Source.actorRef(1, OverflowStrategy.fail) // if we ever want to talk to event source, implement this stub
    )

}
