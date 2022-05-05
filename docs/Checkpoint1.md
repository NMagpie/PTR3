# PTR Lab 3: Message Broker

## Made by: Sorochin Nichita, FAF-191

## Used technologies:

- Scala Language - 2.13.0

- Akka Library - 2.6.18

- Json4s Jackson - 4.1.0M1

## Actors:

## 1. MainSupervisor - initializes all other actors and supervises them.
### Sends
- Two messages `CreateListener` to `ListenerSupervisor`
### Receives
- Messages `CreateListenerSupervisor`, `CreateSenderManager`, `CreateSenderSupervisor`, `CreateSenderScaler` from initial function


## 2. ListenerSupervisor - initializes both `Listeners` for both streams and supervises them.
### Receives
- `CreateListener` from `MainSupervisor`

## 3. Listener - listens for the SSE stream from corresponding endpoint of Producer Server
### Sends and receives
- `Tweet` message from itself to process messages received from SSE stream
### Sends
- `Message` message to the corresponding `Topic worker`

## 4. SenderSupervisor - initializes `Senders` and supervises them.
### Receives
- `CreateSender` from `SenderManager`

## 5. SenderManager - first actor to interact with client (consumers), after communicates with `SenderSupervisor` (or `SenderScaler`) to create senders for connection, delegates subscriptions and much more.
### Sends
- `CreateSender` to `SenderSupervisor`
### Receives
- `Connect` message from the Client Side and creates `Sender worker`

## 6. Sender - the actor being initialized by SenderSupervisor and has TCP connection with the client side, receives and sends message from/to it
### Sends
- `Topics` message, which contains list of all available topics for current moment
- `Message` message with topic and text to the client side
### Receives
- `Subscribe` with list of topics client side want to subscribe

## 7. Topic worker - actor, which saves all corresponding topic messages and sends it to senders if they are subscribed to it.
### Sends
- `Message` message to `Sender` with topic and text
### Receives
- `Message` from `Listener` with topic and text
- `Subscribe` (to topic) from `Sender`

## Diagrams:

### General Message Flow

![general-mf](docs/images/generalmf.png)

### Message Broker Message Flow

![mb-mf](docs/images/mbmf.png)

### Supervisor Tree

![suptree](docs/images/suptree.png)
