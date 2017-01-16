package com.example.codec

import com.example.dispatch._

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.CharSequenceReader

object MessageEventCodec {
  def decode(str: String): MessageEvent = MessageEventDecoder(str)
  def encode(me: MessageEvent): String = MessageEventEncoder(me)
}

private object MessageEventDecoder extends Parsers {

  type Elem = Char
  def apply(in: String): MessageEvent = apply(new CharSequenceReader(in))
  def apply(in: Input): MessageEvent = msgEvent(in).get

  def msgEvent: Parser[MessageEvent] = broadcast | privateMsg | followMsg | unfollowMsg | statusUpdate

  def broadcast: Parser[BroadcastMessage] = seqNum <~ '|' <~ 'B' ^^ BroadcastMessage
  def privateMsg: Parser[PrivateMessage] = seqSrcDst('P') ^^ { case(sn,id1,id2) => PrivateMessage(sn, id1, id2) }
  def followMsg: Parser[FollowMessage] = seqSrcDst('F') ^^ { case(sn,id1,id2) => FollowMessage(sn, id1, id2) }
  def unfollowMsg: Parser[UnfollowMessage] = seqSrcDst('U') ^^ { case(sn,id1,id2) => UnfollowMessage(sn, id1, id2) }
  def statusUpdate: Parser[StatusUpdate] = seqNum ~ '|' ~ 'S' ~ '|' ~ id ^^ { case (sn ~_~_~_~ id) => StatusUpdate(sn, id) }

  // helpers

  private def seqSrcDst(c: Char): Parser[(Int,String,String)] =  seqNum ~ '|' ~ c ~ '|' ~ id ~ '|' ~ id ^^ {
    case (sn ~_~_~_~ id1 ~_~ id2) => (sn, id1, id2)
  }

  private def seqNum: Parser[Int] = rep1(digit) ^^ (_.mkString.toInt)
  private def id: Parser[String] = rep1(digit) ^^ (_.mkString)
  private def digit: Parser[Char] = acceptIf(DIGITS.contains)(x => s"$x is not a digit")
  private val DIGITS: Set[Char] = Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
}

private object MessageEventEncoder {

  def apply(me: MessageEvent): String = me match {
    case BroadcastMessage(seqNum) => s"$seqNum|B"
    case PrivateMessage(seqNum, src, dst) => s"$seqNum|P|$src|$dst"
    case FollowMessage(seqNum, src, dst) => s"$seqNum|F|$src|$dst"
    case UnfollowMessage(seqNum, src, dst) => s"$seqNum|U|$src|$dst"
    case StatusUpdate(seqNum, src) => s"$seqNum|U|$src"
  }
}