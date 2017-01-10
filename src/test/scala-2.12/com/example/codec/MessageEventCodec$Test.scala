package com.example.codec
import com.example.dispatch.{BroadcastMessage, InvalidMessage}
import org.scalatest.{Matchers, WordSpec}

class MessageEventCodec$Test extends WordSpec with Matchers {

  import MessageEventCodec.{decode,encode}

  "MessageEventEncoder" should {

    "decode a BroadcastMessage" when {

      "given a multi-digit sequence number" in {
        decode("123\\|B") shouldEqual BroadcastMessage(123)
      }

      "given a single-digit sequence number" in {
        decode("1\\|B") shouldEqual BroadcastMessage(1)
      }
    }

    "decode an InvalidMessage" when {

      "given junk" in {
        decode("foobar") shouldEqual InvalidMessage("foobar")
      }

      "given a malformed BroadcastMessage" in {
        decode("A\\|B") shouldEqual InvalidMessage("A\\|B")
      }
    }

    "encode a BroadcastMessage" when {

      "given a single-digit sequence number" in {
        encode(BroadcastMessage(1)) shouldEqual "1\\|B"
      }

      "given a double-digit sequence number" in {
        encode(BroadcastMessage(123)) shouldEqual "123\\|B"
      }
    }
  }
}
