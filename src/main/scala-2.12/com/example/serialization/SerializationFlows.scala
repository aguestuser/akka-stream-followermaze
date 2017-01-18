package com.example.serialization

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Framing}
import akka.util.ByteString
import com.example.event.MessageEvent
import MessageEventCodec.{encode, decode}

object SerializationFlows {

  // constants

  val crlf: String = "\n" // for some reason `\n` *not* `\r\n`, works with test scripts

  private val crlfBytes = ByteString(crlf)
  private val maxFrameLength = 1024
  private val truncation = true

  // interface

  val deserializationFlow: Flow[ByteString, String, NotUsed] =
    Flow[ByteString]
      .via(Framing.delimiter(crlfBytes, maxFrameLength, truncation))
      .map(_.utf8String)

  val serializationFlow: Flow[String, ByteString, NotUsed] =
    Flow[String]
      .map(_ + crlf)
      .map(ByteString(_))

  val eventSourceDeserializationFlow: Flow[ByteString, MessageEvent, NotUsed] =
    Flow[ByteString]
      .via(deserializationFlow)
      .map(decode)

  val clientSerializationFlow: Flow[MessageEvent, ByteString, NotUsed] =
    Flow[MessageEvent]
      .map(encode)
      .via(serializationFlow)

}
