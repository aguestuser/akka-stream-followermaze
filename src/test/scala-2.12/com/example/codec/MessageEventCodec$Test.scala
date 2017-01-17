package com.example.codec
import com.example.dispatch._
import com.example.event._
import org.scalatest.{Matchers, WordSpec}

class MessageEventCodec$Test extends WordSpec with Matchers {

  import MessageEventCodec.{decode,encode}

  "MessageEventEncoder" should {

    "decode a BroadcastMessage" in {
      decode("123|B") shouldEqual BroadcastMessage(123)
    }

    "encode a BroadcastMessage" in {
      encode(BroadcastMessage(123)) shouldEqual "123|B"
    }

    "decode a Private Message" in {
      decode("123|P|1|2") shouldEqual PrivateMessage(123, "1", "2")
    }

    "encode a Private Message" in {
      encode(PrivateMessage(123, "1", "2")) shouldEqual "123|P|1|2"
    }

    "decode a Follow Message" in {
      decode("123|F|1|2") shouldEqual FollowMessage(123, "1", "2")
    }

    "encode a Follow Message" in {
      encode(FollowMessage(123, "1", "2")) shouldEqual "123|F|1|2"
    }

    "decode an Unfollow Message" in {
      decode("123|U|1|2") shouldEqual UnfollowMessage(123, "1", "2")
    }

    "encode an Unfollow Message" in {
      encode(UnfollowMessage(123, "1", "2")) shouldEqual "123|U|1|2"
    }

    "decode an Status Update" in {
      decode("123|S|1") shouldEqual StatusUpdate(123, "1")
    }

    "encode an Status Update" in {
      encode(StatusUpdate(123, "1")) shouldEqual "123|S|1"
    }
  }
}
