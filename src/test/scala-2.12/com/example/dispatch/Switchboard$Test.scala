package com.example.dispatch

import akka.actor.{ActorRef, ActorSystem}
import akka.io.UdpConnected.Disconnect
import akka.testkit.TestProbe
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import com.example.serialization.MessageEventCodec.encode
import com.example.event._

class Switchboard$Test extends WordSpec with Matchers {

  "Switchboard" should {

    import Switchboard._

    implicit val system = ActorSystem()
    val alice = TestProbe()
    val bob = TestProbe()
    val timeout = 200 milli

    "provide an empty switchboard" in {
      Switchboard.empty shouldEqual
        Switchboard(
          Map.empty[String, ActorRef],
          Map.empty[String, Set[String]],
          1,
          Map.empty[Int, MessageEvent])
    }

    "handle a subscription" in {

      val beforeState = Switchboard(
        Map("111" -> alice.ref),
        Map.empty[String, Set[String]],
        1,
        Map.empty[Int, MessageEvent])

      handleConnectionEvent(Subscribe("222", bob.ref))(beforeState) shouldEqual beforeState.copy(
        subscribers = Map("111" -> alice.ref, "222" -> bob.ref)
      )
    }

    "handle an unsubscription" in {

      val beforeState = Switchboard(
        Map("111" -> alice.ref),
        Map.empty[String, Set[String]],
        1,
        Map.empty[Int, MessageEvent]
      )

      handleConnectionEvent(Unsubscribe("111"))(beforeState) shouldEqual
        Switchboard.empty
    }

    "handle an event source termination" in {

      val beforeState = Switchboard(
        Map("111" -> alice.ref, "222" -> bob.ref),
        Map.empty[String, Set[String]],
        1,
        Map[Int, MessageEvent](1 -> BroadcastMessage(1))
      )

      handleConnectionEvent(EventSourceTerminated)(beforeState) shouldEqual
        Switchboard.empty

      alice.expectMsg(Disconnect)
      bob.expectMsg(Disconnect)
    }


    "handle a broadcast message" when {

      val beforeState = Switchboard(
        Map("111" -> alice.ref, "222" -> bob.ref),
        Map.empty[String, Set[String]],
        1,
        Map(2 -> BroadcastMessage(2), 4 -> BroadcastMessage(4))
      )

      "the message is next in sequence: enqueue and send (all in sequence)" in {

        handleMessage(1, BroadcastMessage(1))(beforeState) shouldEqual
          Switchboard(beforeState.subscribers, beforeState.followers, 3,
            Map(4 -> BroadcastMessage(4)))

        alice.expectMsgAllOf(timeout, BroadcastMessage(1), BroadcastMessage(2))
        bob.expectMsgAllOf(timeout, BroadcastMessage(1), BroadcastMessage(2))
      }

      "the message is not next in sequence: only enqueue" in {

        handleMessage(3, BroadcastMessage(3))(beforeState) shouldEqual
          Switchboard(
            beforeState.subscribers,
            beforeState.followers,
            beforeState.nextMsgId,
            Map(2 -> BroadcastMessage(2), 3 -> BroadcastMessage(3), 4 -> BroadcastMessage(4))
          )

        alice.expectNoMsg(timeout)
        bob.expectNoMsg(timeout)
      }
    }

    "handle a private message" in {

      // only test scenario in which message is next in sequence
      // as other scenarios are adequately covered by broadcast tests

      val beforeState = Switchboard(
        Map("111" -> alice.ref, "222" -> bob.ref),
        Map.empty[String, Set[String]],
        1,
        Map.empty[Int, MessageEvent]
      )

      handleMessage(1, PrivateMessage(1, "111", "222"))(beforeState) shouldEqual Switchboard(
        beforeState.subscribers, beforeState.followers, 2,
        Map.empty[Int, MessageEvent])

      alice.expectNoMsg(timeout)
      bob.expectMsg(PrivateMessage(1, "111", "222"))
    }

    "handle a follow message" in {

      val beforeState = Switchboard(
        Map("111" -> alice.ref, "222" -> bob.ref),
        Map.empty[String, Set[String]],
        1,
        Map.empty[Int, MessageEvent]
      )

      handleMessage(1, FollowMessage(1, "222", "111"))(beforeState) shouldEqual Switchboard (
        beforeState.subscribers,
        Map("111" -> Set("222")),
        2,
        Map.empty[Int, MessageEvent]
      )

      alice.expectMsg(timeout, FollowMessage(1, "222", "111"))
      bob.expectNoMsg(timeout)

    }

    "handle an unfollow message" in {

      val beforeState = Switchboard(
        Map("111" -> alice.ref, "222" -> bob.ref),
        Map("111" -> Set("222")),
        1,
        Map.empty[Int, MessageEvent]
      )

      handleMessage(1, UnfollowMessage(1, "222", "111"))(beforeState) shouldEqual beforeState.copy(
        followers = Map("111" -> Set.empty[String]),
        nextMsgId = 2
      )

      alice.expectNoMsg(timeout)
      bob.expectNoMsg(timeout)

    }

    "handle a status update" when {

      val baseState = Switchboard(
        Map("111" -> alice.ref, "222" -> bob.ref),
        Map.empty[String, Set[String]],
        1,
        Map.empty[Int, MessageEvent]
      )

      "the sender has no followers" in {

        handleMessage(1, StatusUpdate(1, "222"))(baseState) shouldEqual baseState.copy(
          nextMsgId = 2
        )

        alice.expectNoMsg(timeout)
        bob.expectNoMsg(timeout)
      }

      "the sender has a follower" in {

        val beforeState = baseState.copy(
          followers = Map("111" -> Set("222")) // bob is following alice
        )

        handleMessage(1, StatusUpdate(1, "111"))(beforeState) shouldEqual beforeState.copy(
          nextMsgId = 2
        )

        alice.expectNoMsg(timeout)
        bob.expectMsg(timeout, StatusUpdate(1, "111"))
      }

      "the sender has many followers" in {

        val beforeState = baseState.copy(
          followers = Map("111" -> Set("111", "222")) // alice and bob are following alice
        )

        handleMessage(1, StatusUpdate(1, "111"))(beforeState) shouldEqual beforeState.copy(
          nextMsgId = 2
        )

        alice.expectMsg(StatusUpdate(1, "111"))
        bob.expectMsg(StatusUpdate(1, "111"))
      }
    }

    "helpers" should {

      "add a subscriber to the switchboard" when {

        "the switchboard is empty" in {

          val beforeState = Switchboard.empty

          addSubscriber("111", alice.ref)(Switchboard.empty) shouldEqual
            Switchboard(
              Map("111" -> alice.ref), beforeState.followers, beforeState.nextMsgId, beforeState.messages)
        }

        "the switchboard is not empty" in {

          val beforeState = Switchboard(
            Map("111" -> alice.ref),
            Map.empty[String, Set[String]],
            1,
            Map.empty[Int, MessageEvent]
          )

          addSubscriber("222", bob.ref)(beforeState) shouldEqual
            Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref), beforeState.followers, beforeState.nextMsgId, beforeState.messages)
        }
      }

      "add a follower" when {

        "there are no pre-existing followers" in {

          val beforeState = Switchboard.empty

          addFollower("111", "222")(beforeState) shouldEqual
            beforeState.copy(
              followers = Map("222" -> Set("111"))
            )
        }

        "there are pre-existing followers" in {

          val beforeState = Switchboard.empty.copy(
            followers = Map("111" -> Set("3"), "222" -> Set("111" ,"3"))
          )

          addFollower("222", "111")(beforeState) shouldEqual
            beforeState.copy(
              followers = Map("111" -> Set("3", "222"), "222" -> Set("111" ,"3"))
            )
        }
      }

      "remove a follower" when {

        "the target has followers" in {
          val beforeState = Switchboard.empty.copy(
            followers = Map("111" -> Set("222", "333"))
          )

          removeFollower("222", "111")(beforeState) shouldEqual beforeState.copy(
            followers = Map("111" -> Set("333"))
          )
        }

        "the target has no followers" in {
          removeFollower("222", "111")(Switchboard.empty) shouldEqual Switchboard.empty.copy(
            followers = Map("111" -> Set.empty[String])
          )
          // this oddball behavior, but okay for scope of this assignment
          // alternative would be to add mapping from string to empty set
          // every time a subscriber is added to the Switchboard, which seems unnecessary
        }

      }

      "enqueue a message" when {

        "the message queue is empty" in {

          val beforeState = Switchboard.empty

          enqueueMessage(1, BroadcastMessage(1))(beforeState) shouldEqual
            beforeState.copy(
              messages = Map(1 -> BroadcastMessage(1))
            )
        }

        "the message queue is not empty" in {

          val beforeState = Switchboard.empty.copy(
            messages = Map(1 -> BroadcastMessage(1))
          )

          enqueueMessage(2, BroadcastMessage(2))(beforeState) shouldEqual
            beforeState.copy(
              messages = Map(1 -> BroadcastMessage(1), 2 -> BroadcastMessage(2))
            )
        }
      }

      "drain the message queue" when {

        "the queue is empty" in {
          drainMessageQueue(Switchboard.empty) shouldEqual Switchboard.empty
        }

        "the next message is not in the queue" in {
          val beforeState = Switchboard(
            Map("111" -> alice.ref),
            Map.empty[String, Set[String]],
            1,
            Map(2 -> BroadcastMessage(2))
          )

          drainMessageQueue(beforeState) shouldEqual beforeState
          alice.expectNoMsg(timeout)
        }

        "the next message is in the queue" when {

          "the next message is a broadcast message" in {

            val beforeState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> BroadcastMessage(1))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(beforeState.subscribers, beforeState.followers, 2,
              Map.empty[Int, MessageEvent])

            alice.expectMsg(BroadcastMessage(1))
            bob.expectMsg(BroadcastMessage(1))
          }

          "the next message is a private message" when {

            val baseState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> PrivateMessage(1, "111", "222"))
            )

            "the recipient is a subscriber" in {

              drainMessageQueue(baseState) shouldEqual baseState.copy(
                nextMsgId = 2,
                messages = Map.empty[Int, MessageEvent]
              )

              alice.expectNoMsg(timeout)
              bob.expectMsg(PrivateMessage(1, "111", "222"))
            }

            "the recipient is not a subscriber -- do not throw"in {
              val beforeState = baseState.copy(
                subscribers = Map.empty[String, ActorRef]
              )

              drainMessageQueue(beforeState) shouldEqual beforeState.copy(
                nextMsgId = 2,
                messages = Map.empty[Int, MessageEvent]
              )
            }

          }

          "the next message is a follow message" when {

            val baseState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> FollowMessage(1, "111", "222"))
            )

            "the recipient is a subscriber" in {

              drainMessageQueue(baseState) shouldEqual Switchboard(
                baseState.subscribers,
                Map("222" -> Set("111")),
                2,
                Map.empty[Int, MessageEvent]
              )

              alice.expectNoMsg(timeout)
              bob.expectMsg(FollowMessage(1, "111", "222"))
            }

            "the recipient is not a subscriber" in {

              val beforeState = baseState.copy(
                subscribers = Map.empty[String, ActorRef]
              )

              noException should be thrownBy drainMessageQueue(beforeState)

            }

          }

          "the next message is an unfollow message" in {

            val beforeState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map("222" -> Set("111")),
              1,
              Map(1 -> UnfollowMessage(1, "111", "222"))
            )

            drainMessageQueue(beforeState) shouldEqual beforeState.copy(
              nextMsgId = 2,
              followers = Map("222" -> Set.empty[String]),
              messages = Map.empty[Int, MessageEvent]
            )

            alice.expectNoMsg(timeout)
            bob.expectNoMsg(timeout)
          }

          "the next message is a status update" when {

            val baseState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> StatusUpdate(1, "111"))
            )

            "the sender has no followers" in {

              drainMessageQueue(baseState) shouldEqual baseState.copy(
                nextMsgId = 2,
                messages = Map.empty[Int, MessageEvent]
              )

              alice.expectNoMsg(timeout)
              bob.expectNoMsg(timeout)
            }

            "the sender has followers" in {

              val beforeState = baseState.copy(
                followers = Map("111" -> Set("111", "222")) // alice is following herself
              )

              drainMessageQueue(beforeState) shouldEqual beforeState.copy(
                nextMsgId = 2,
                messages = Map.empty[Int, MessageEvent]
              )

              alice.expectMsg(timeout, StatusUpdate(1, "111"))
              bob.expectMsg(timeout, StatusUpdate(1, "111"))
            }

            "the sender has a follower that is not subscribed" in {

              val beforeState = baseState.copy(
                followers = Map("111" -> Set("333"))
              )

              noException should be thrownBy drainMessageQueue(beforeState)
            }
          }
        }

        "the next N messages are in the queue" when {

          "all messages are broadcast messages" in {

            val beforeState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> BroadcastMessage(1),2 -> BroadcastMessage(2))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(beforeState.subscribers, beforeState.followers, 3,
              Map.empty[Int, MessageEvent])

            // enforce order of messages
            alice.receiveN(2) shouldEqual Seq(BroadcastMessage(1), BroadcastMessage(2))
            bob.receiveN(2) shouldEqual Seq(BroadcastMessage(1), BroadcastMessage(2))
          }

          "all messages are private messages" in {

            val beforeState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> PrivateMessage(1, "111", "222"), 2 -> PrivateMessage(2, "222", "111"))
            )

            drainMessageQueue(beforeState) shouldEqual Switchboard(beforeState.subscribers, beforeState.followers, 3,
              Map.empty[Int, MessageEvent])

            alice.expectMsg(PrivateMessage(2, "222", "111"))
            bob.expectMsg(PrivateMessage(1, "111", "222"))
          }

          "the messages are a mix of message types" in {

            val beforeState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map("111" -> Set("222")),
              1,
              Map(
                1 -> BroadcastMessage(1),
                2 -> PrivateMessage(2, "222", "111"),
                3 -> FollowMessage(3, "111", "222"),
                4 -> UnfollowMessage(4, "222", "111"),
                5 -> StatusUpdate(5, "222"),
                6 -> StatusUpdate(6, "111"),
                7 -> PrivateMessage(7, "111", "222")
              )
            )

            drainMessageQueue(beforeState) shouldEqual beforeState.copy(
              followers = Map("111" -> Set.empty[String], "222" -> Set("111")),
              nextMsgId = 8,
              messages = Map.empty[Int, MessageEvent]
            )

            // enforce order of alice & bob's messages
            alice.receiveN(3) shouldEqual Seq(
              BroadcastMessage(1),
              PrivateMessage(2, "222", "111"),
              // omit unfollow message from bob
              StatusUpdate(5, "222")
            )
            bob.receiveN(3) shouldEqual Seq(
              BroadcastMessage(1),
              FollowMessage(3, "111", "222"),
              // omit status update from alice (who bob is not following)
              PrivateMessage(7, "111", "222")
            )
          }
        }

        "the next N messages and an out-of-order message are in the queue" when {

          "all messages are broadcast messages" in {

            val beforeState = Switchboard(
              Map("111" -> alice.ref, "222" -> bob.ref),
              Map.empty[String, Set[String]],
              1,
              Map(1 -> BroadcastMessage(1), 2 -> BroadcastMessage(2), 4 -> BroadcastMessage(4)))

            drainMessageQueue(beforeState) shouldEqual
              beforeState.copy(
                nextMsgId = 3,
                messages = Map(4 -> BroadcastMessage(4))
              )

            alice.receiveN(2) shouldEqual Seq(
              BroadcastMessage(1),
              BroadcastMessage(2)
            )
            bob.receiveN(2) shouldEqual Seq(
              BroadcastMessage(1),
              BroadcastMessage(2)
            )
          }
        }
      }
    }
  }
}
