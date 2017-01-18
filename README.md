# Followermaze

An implementation of the SoundCloud `followermaze` code challenge in Scala.

# Dependencies

I have verified the enclosed application passes the verification scripts on a Thinkpad 460s running Debian Stretch with the following programs installed:

* jdk v. 1.8.0
* scala v. 2.12.1
* sbt v. 0.13.13

# Run instructions

The below instructions assume that `$BASE_DIR` is the file in which this README file is located. 

Open two terminal windows.In the first, start the application:

```
$ cd $BASE_DIR
$ sbt run
```

In the second, start the verification script:

```
$ cd $BASE_DIR/bin
$ ./followermaze.sh
```

# Tests

To run the tests, run:
 
```
$ cd $BASE_DIR
$ sbt test
```

To see the coverage report, open `$BASE_DIR/index.html` in a browser.

# Implementation Notes

## Design Decisions

**Akka-streams**

I chose to use `akka-streams` as the baseline library for the project primarily because of its expressiveness. The library provides functional abstractions called "flows" for performing reusable units of transformation on reactive streams. Flows encapsulate most of the statefulness involved in stream processing and yield concise, modular, and composable code. As a result, I find that `akka-streams` makes complex concurrent operations easier to reason about and maintain over time. The library is also quite performant, well-documented, and comes with handy built-in unit-testing tools.

**Actors**

A significant challenge doing network programming in a functional style is the necessity of maintaining the (constantly mutating) state of socket connections and message queues while avoiding race conditions. Actors provide a handy bridge. Because Actors offer strong one-at-a-time/FIFO guarantees on message processing, one may perform mutations in a thread-safe manner by wrapping state inside an Actor and mutating it every time a message is received. This eliminates both the fear of race conditions and the need to reason about mutexes. Because references to Actors are themselves immutable, we may expose the Actor reference to a purely functional data processing pipeline, while safely encapsulating the mutation behind this immutable reference. (They are also lightweight and fault-tolerant, which is ideal for network programming scenarios.)

**The "DispatchActor"**

The design for this program uses `akka-streams` to pass streams of client-registration and event-source messages through purely functional pipelines to a `DispatchActor`, which safely wraps a mutating collection of client sockets, follower relationships, and queued messages. Thus, we can track the changing set of clients and followers (and the routing rules between them), reorder messages to impose sequencing, and emit messages back into a purely functional pipeline. We get the benefits of testability, reliability, and ease-of-reasoning provided by functional programming, while also enjoying the flexibility of a more imperative command-drive style. We can have our cake and eat it too!

**"Switchboard" State Machine**

With stream processing handled by `akka-streams` and mutation safely wrapped inside a Dispatch Actor, we have a choice as to how to actually accomplish the mutation of clients, followers, and message queue. I chose to model this mutation as an (almost) purely functional state machine called `Switchboard`.

The `DispatchActor` holds a mutable reference to an immutable `Switchboard` case class. Every time the `DispatchActor` receives a message, it runs the `Switchboard` state machine, which consumes and produces a new `Switchboard`. (The only non-functional aspect of the `Switchboard` is that it performs the side-effect of emitting a message back out into the `akka-streams` pipeline if the rules of the state machine so dictate.)

As a result, the lion's share of the business logic pertaining to message sorting and routing is encapsulated within a self-contained module of (mostly) pure functions. In other words, our core logic can  be designed and unit-tested without reference to the transport layer. I found this extremely useful in reasoning about some of the more complicated corner cases of the problem statement, and would argue that the code is more maintainable as a result of this choice.

**Parser Combinators**

I used parser combinators to decode the messages coming off the wire because they are so darn expressive and it's a delight to get to use them any time I can! To my mind, they are one of the crown jewels of functional programming, showing just how concise and expressive monadic combinator patterns can be.

## Execution Flow

* The entry point to the app is `Main`, where we bind and listen to 2 TCP ports:
  * On 9099: we listen for messages from user clients and stream them through the `subscriptionFlow`
  * On 9090: we listen for messages from the event source, and stream them through the `relayFlow`

* The `subscriptionFlow`:
  * deserializes a stream of bytes into a string every time it sees a CRLF delimiter
  * assumes that every string it receives is a registration id (since clients send no other messages)
  * materializes a new Actor (specific to the client that just registered) and subscribes it to messages from the `DispatchActor` (using the id it parsed in the last step)
  * emits messages sent from the `DispatchActor` to this client-specific Actor back into the stream
  * serializes those messages into bytes, which are placed back on the client socket

* The `relayFlow`
  * deserializes a stream of bytes into a string every time it sees a CRLF delimiter
  * decodes the string into a `MessageEvent` using the combinators in `MessageEventCodec`
  * sends the `MessageEvent` to the `DispatchActor`
  * materializes a new event-source-specific Actor that will emit all messages sent to it back to the event-source socket
  * since we never want to send anything to the event source, we don't ever send anything to this actor, and the event-source receives no messages

* The `DispatchActor`
  * starts off wrapping an empty `Switchboard`
  * responds to messages by calling either `handleConnectionEvent` or `handleMessageEvent`
    * both functions consume one message and the current `Switchboard` and return a new `Switchboard` with an updated state (adding clients, enqueueing, or dequeing and sending messages as specified by the rules of the state machine)
  * implicitly: imposes a 1-at-a-time/FIFO guarantee on messages that makes it possible to treat the `Switchboard` as a state machine, since we may safely assume that only one operation happens on it at a time

* The `Switchboard`:
  * adds and removes subscribers (references to actors wrapping client sockets) from an immutable map
  * adds and removes followers from an immutable map
  * increments a `nextMsgId` counter every time a message is sent
  * enqueues messages every time one is received by adding them to an immutable map
  * recursively drains the message queue every time a message is received.
    * on each recursive call it:
      * does nothing if the message specified by `nextMsgId` is not in the queue
      * sends the message matching `nextMsgId` if it is in the queue, incrementing `nextMsgId` and removing the sent message from the queue
      * routes the message to a subset of clients based on the message type and the subscribers/followers list
      * modifies the follower list if appropriate
    * when messages are "sent" the are transmitted to actors in the `subscriptionFlow` pipeline, whence they are emitted back into the `subscriptionFlow` stream, serialized and placed back on the appropriate client socket
 
 * FIN!