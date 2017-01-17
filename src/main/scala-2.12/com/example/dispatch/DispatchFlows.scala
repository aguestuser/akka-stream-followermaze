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

  // serialization
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

  /**
    * `registerFlow` consumes a stream of id strings, and converts them into actors
    *  that receive messages from the DispatchActor and emit them back to the client socket.
    *
    * When it receives an id, it completes the Flow from which it got the stream.
    * (This is the effect of `flatMapConcat`).
    *
    * Then it creates an actor that will emit back into the stream,
    * (and thus back to the client socket that is both source and sink of the stream).
    *
    * As soon as the actor is materialized, it sends a subscribe message to the DispatchActor
    * with the client's id, so that if the DispatchActor sends it a message, that message
    * will be emitted back to the client socket.
    *
    * */

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

  /**
    *
    * `relayFlow` routes a stream of incoming messages from the event source socket to the
    * DispatchActor, and emits nothing back to the event source socket.
    *
    *
    * Incoming messages are passed to the DispatchActor wrapped in a Sink. When the stream
    * producing these messages is terminated, the DispatchActor is sent an `EventSourceTerminated`
    * message.
    *
    * Outgoing messages (which will be emited back to the event source)
    * are produced by a Source wrapping an actor materialized along with the flow.
    *
    * Whatever messages are received by this actor will be emited back to the event source
    * socket. However, as the event source socket expects no messages, we leave this
    * actor as a stub and do not send it anything.
    *
    * */

  private def relayFlow(implicit dispatchActor: ActorRef): Flow[MessageEvent, String, NotUsed] =
    Flow.fromSinkAndSource(
      Sink.actorRef[MessageEvent](dispatchActor, EventSourceTerminated),
      Source.actorRef(1, OverflowStrategy.fail) // if we ever want to talk to event source, implement this stub
    )

}
