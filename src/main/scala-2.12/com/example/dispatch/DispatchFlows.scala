package com.example.dispatch

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.example.serialization.SerializationFlows._
import com.example.event.{EventSourceTerminated, MessageEvent, Subscribe}

object DispatchFlows {

  val subscriptionBufferSize: Int = 100

  // client subscription

  def subscribeFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    deserializationFlow
      .via(registerFlow)
      .via(clientSerializationFlow)

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

  private def registerFlow(implicit dispatchActor: ActorRef): Flow[String, MessageEvent, NotUsed] =
    Flow[String].flatMapConcat(id =>
      Source.actorRef[MessageEvent](subscriptionBufferSize, OverflowStrategy.fail)
        .mapMaterializedValue(client => dispatchActor ! Subscribe(id, client))
    )

  // event source transmission

  def eventSourceFlow(implicit dispatchActor: ActorRef): Flow[ByteString, ByteString, NotUsed] =
    eventSourceDeserializationFlow
      .via(relayFlow)
      .via(serializationFlow)

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
    * Outgoing messages (which will be emitted back to the event source)
    * are produced by a Source wrapping an actor materialized along with the flow.
    *
    * Whatever messages are received by this actor will be emitted back to the event source
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
