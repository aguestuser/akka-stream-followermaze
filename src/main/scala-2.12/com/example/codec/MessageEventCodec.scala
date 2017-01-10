package com.example.codec

import com.example.dispatch.{BroadcastMessage, InvalidMessage, MessageEvent}

import scala.util.parsing.input.CharSequenceReader

object MessageEventCodec {
  def decode(str: String): MessageEvent = MessageEventDecoder(str)
  def encode(me: MessageEvent): String = MessageEventEncoder(me)
}

private object MessageEventDecoder extends StringDecoder {

  def apply(in: String): MessageEvent = apply(new CharSequenceReader(in))
  def apply(in: Input): MessageEvent = msgEvent(in).get

  def msgEvent: Parser[MessageEvent] = broadcast | invalid

  def broadcast: Parser[BroadcastMessage] = int <~ pipe <~ 'B' ^^ BroadcastMessage
  def invalid: Parser[InvalidMessage] = rep(anychar) ^^ (x => InvalidMessage(("" /: x)(_+_)))
}

private object MessageEventEncoder {

  def apply(me: MessageEvent): String = me match {
    case BroadcastMessage(seqNum) => s"$seqNum\\|B"
    case InvalidMessage(msg) => s"Invalid message: $msg"
  }
}