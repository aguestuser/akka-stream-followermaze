# User Stories for Follower Maze

# 0. Connect to server and register id

## Value
* precondition of users sending messages to one another

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
| x | 1 | A server running on `localhost` | An `eventSource` tries to connect to `localhost:9009` | The connection is accepted and the server logs `Connected to event source.` |
| x | 2 | " | A client connects to `localhost:9099` and sends the message `111\n` | The connection is accepted and the server logs: `New client with id 1 added. 1 total client(s).` |
| x | 3 | " | A client connects to `localhost:9099` and sends the messasge `222\n` | The connection is accepted and the server logs: `New client with id 2 added. 2 total client(s).` | 
| x | 4 | " AND a connected event source and connected client | The event source disconnects | The disconnection is logged and the client is disconnected |
| x | 5 | " AND " | The event source disconnects | The disconnection is logged and the server will not attempt to forward messages to the client |

# 1. Send broadcast messages (in order)

## Value
* allow users to talk to all of their friends at once

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
| x | 1 | A running server, a connected `eventSource` and two clients, Alice and Bob | The `eventSource` sends the server a `Broadcast` message w/ sequence # 1 | Alice and Bob both receive the message |
| x | 2 | " | The `eventSource` sends a `Broadcast` message w/ sequence # 2 | Alice and Bob will both receive both broadcast messages in the proper sequence |
| x | 3 | " | The `eventSource` sends a `Broadcast` message w/ sequence # 2, *then* a `Broadcast` message w/ sequence # 1 | Alice and Bob will both receive both broadcast messages in the proper sequence |    
| x | 4 | " | The `eventSource` sends 1000000 broadcast messages out-of-order | Alice and Bob will both receive 1000000 broadcast messages in the proper sequence |


## Definitions

* In AC1: the broadast message is of the form `1|B` (as is the notification received by Alice & Bob)
* In AC1: the broadast message is of the form `2|B`
* In AC2: the broadcast messages are of the form `2|B`, `1|B` (as is the notification received by Alice & Bob)

# 2. Send private messages

## Value
* allow users to send a message that some users, but not others may see

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
| x | 1 | A running server w/ connected event source and clients Alice (id 111) and Bob (id 222)| The `eventSource` sends a private message from Alice to Bob | Bob will receive a notification |  
| x | 2 | " | " |  Alice will not receive a notification |  

## Definitions

* The private message in AC's 1 & 2 is of the form: `42|P|111|222|` (as is the notification received by Alice)

# 3. Send status updates

## Value
* Allow users to receive updates from people they want to follow, but not from people they don't want to follow

## Acceptance Criteria

| x | # | Given | When | Then |
|---|---| ----- | ---- | ---- |
| x | 1 | A running server w/ connected event source and clients Alice (id 1) and Bob (id 2)| The `eventSource` sends a `StatusUpdate` from Alice | Bob will not receive a notification |  
| x | 2 | " | The `eventSource` sends a follow request from Bob to Alice | Alice will receive a `Follow` notification (and the server will record the new relationship) |  
| x | 3 | " AND Bob is following Alice| The `eventSource` sends a `StatusUpdate` from Alice | Bob will receive a `StatusUpdate` notification |
| x | 4 | " AND " | The `eventSource` sends an `Unfollow` event from Bob to Alice | Alice will not receive a notification (and the server will record the new relationship) |
| x | 5 | " AND Bob has followed and unfollowed Alice | The `eventSource` sends an `StatusUpdate` from Alice | Bob will not receive a notification |
| x | 6 | " | The `eventSource` sends a `StatusUpdate` from Alice with sequence # 2, followed by a `Follow` event from Bob to Alice with sequence # 1 | Bob will receive a `Follow` notification and a `StatusUpdate` (in that order) |


## Definitions:

* AC1: the `StatusUpdate` event is of the form `1|S|1`
* AC2: the `Follow` event is of the form `1|F|222|111` (as is the notification received by Alice)
* AC3: the `Follow` update is the same as AC2. The`StatusUpdate` event is of the form `2|S|111`
* AC4: the `Follow` and `StatusUpdate` events are the same as in AC3. The `Unfollow` event is of the form `3|U|222|111`
* AC5: the `Follow`, `StatusUpdate`, and `Unfollow` events are the same as in AC4. The `StatusUpdate` event is of the form `4|S|1`
* AC6: the `Follow` and `StatusUpdate` events are the same as in AC2 & AC3
