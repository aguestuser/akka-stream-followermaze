# User Stories for Follower Maze

# 0. Connect to server and register id

## Value
* precondition of users sending messages to one another

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
| x | 1 | A server running on `localhost` | An `eventSource` tries to connect to `localhost:9009` | The connection is accepted and the server logs `Connected to event source.` |
| x | 2 | " | A client connects to `localhost:9099` and sends the message `1\n` | The connection is accepted and the server logs: `New client with id 1 added. 1 total client(s).` |
| x | 3 | " | A client connects to `localhost:9099` and sends the messasge `2\n` | The connection is accepted and the server logs: `New client with id 2 added. 2 total client(s).` | 
| x | 4 | " AND a connected event source and connected client | The event source disconnects | The disconnection is logged and the client is disconnected |
|   | 5 | " AND " | The event source disconnects | The disconnection is logged and the server will not attempt to forward messages to the client |

# 1. Send a broadcast message

## Value
* allow users to talk to all of their friends at once

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
| x | 1 | A running server, a connected `eventSource` and two clients, Alice and Bob | The `eventSource` sends the server a `Broadcast` message (as defined below) | Alice and Bob both receive the message |
|   | 2 | " | The `eventSource` sends a `Broadcast` message w/ sequence # 2, *then* a `Broadcast` message w/ sequence # 1 (as defined below) | Alice and Bob will both receive both broadcast messages in the proper sequence |    

## Definitions

* In AC1: the broadast message is of the form `1|B` (as is the notification received by Alice & Bob)
* In AC2: the broadcast messages are of the form `2|B`, `1|B` (as is the notification received by Alice & Bob)

# 2. Send a private message

## Value
* allow users to send a message that some users, but not others may see

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
|   | 1 | A running server w/ connected event source and clients Alice (id 1), Bob (id 2), and Charlie (id 3)| The `eventSource` sends a private message from Alice to Bob | Bob will receive a notification |  
|   | 2 | " | " |  Charlie will not receive a notification |  

## Definitions

* The private message in AC's 1 & 2 is of the form: `42|P|1|2|` (as is the notification received by Alice)

# 3. Send a status update

## Value
* Allow users to receive updates from people they want to follow, but not from people they don't want to follow

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
|   | 1 | A running server w/ connected event source and clients Alice (id 1) and Bob (id 2)| The `eventSource` sends a `StatusUpdate` from Alice | Bob will not receive a notification |  
|   | 2 | " | The `eventSource` sends a follow request from Bob to Alice | Alice will receive a `Follow` notification (and the server will record the new relationship) |  
|   | 3 | " AND Bob is following Alice| The `eventSource` sends a `StatusUpdate` from Alice | Bob will receive a `StatusUpdate` notification |
|   | 4 | " AND " | The `eventSource` sends an `Unfollow` event from Bob to Alice | Alice will not receive a notification (and the server will record the new relationship) |
|   | 5 | " AND Bob has followed and unfollowed Alice | The `eventSource` sends an `StatusUpdate` from Alice | Bob will not receive a notification |
|   | 6 | " | The `eventSource` sends a `Follow` event from Bob to Alice with sequence # 1 and a `StatusUpdate` from Alice with sequence # 2 -- but sequence # 2 arrives before sequence #1| Bob will receive a `Follow` notification and a `StatusUpdate` (in that order) |


## Definitions:

* AC1: the `StatusUpdate` event is of the form `1|S|1`
* AC2: the `Follow` event is of the form `1|F|2|1` (as is the notification received by Alice)
* AC3: the `Follow` update is the same as AC2. The`StatusUpdate` event is of the form `2|S|1`
* AC4: the `Follow` and `StatusUpdate` events are the same as in AC3. The `Unfollow` event is of the form `3|U|2|1`
* AC5: the `Follow`, `StatusUpdate`, and `Unfollow` events are the same as in AC4. The `StatusUpdate` event is of the form `4|S|1`
* AC6: the `Follow` and `StatusUpdate` events are the same as in AC2 & AC3
