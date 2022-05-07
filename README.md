# PTR Lab 3: Message Broker

## Made by: Sorochin Nichita, FAF-191

### Navigation

- [Description](#description)
- [Main Components](#main-components)
  - [Message Broker](#message-broker)
  - [Consumer](#consumer)
  - [Producer](#producer)
  - [RTP-Server](#rtp-server)
- [User Technologies](#used-technologies)

# Description

Unbelievable Message Broker written using Scala Language + Akka Library.

It can receive TCP connections from both `Producers` and `Consumers`.
`Producers` sends data, which mandatory contains topic for every message, and 
then all the messages will be split by the `Topic` criteria.
`Consumers` receives list of topics, and chooses which topic it wants to be subscribed to. 
Afterwards, it successfully will start receiving corresponding messages.

Quantity of `Consumers` and `Producers` may vary and are limited only by Akka TCP 
possibilities. `Consumers` and `Producers` can be developed as you wish 
and using language you wish, the only point it has to create TCP connection and follow
several rules that are mentioned below.

All the diagrams and documents are located in folder `docs`.

# Main Components

- ## Message Broker

The main component, which is described above. 

Located in the folder `messageBroker`.

- ## Consumer

Recommended small app also developer using Scala Language + Akka Library. Connects to
the Message Broker, subscribes and receives messages from it and prints them. Pretty
easy, huh?

Located in the folder `client`.

Of course, you can create your own `Consumer`. How it can connect to the Message Broker:
### 1. Open TCP Connection with Message Broker (Port by default is `8000`)
### 2. Send next JSON:
```json
{
  "connectionType": "Consumer"
}
```
### 3. After, `Consumer` receives list of available topics. It can choose, all, some or none of them.
> Note: If `Consumer` will include topic that do not exist in Message Broker actual list, it
this topic will be ignored.

To send selected topics back, just send the next JSON:
```json
{
  "topics" : 
  [
    "en",
    "es"
  ]
}
```

Instead of `"en", "es"` put your own list of topics.

### 4.  Profit! Your consumer now receives messages.

- ## Producer

Recommended app developed using (guess what?) Scala Language + Akka Library. Connects to
the Message Broker and to the Docker Image `rtp-server`, which is described below.

Located in the folder `producer`.

Of course, you can create your own `Producer`. How it can connect to the Message Broker:
### 1. Open TCP Connection with Message Broker (Port by default is `8000`)
### 2. Send next JSON:
```json
{
  "connectionType": "Produces"
}
```

### 3. Profit! Your producer now can send messages in next JSON pattern:
```json
{
  "id": 12,
  "topic": "en",
  "message": "this is a message"
}
```

- ## RTP-server

The Docker Image courtesy provided by our beloved FAFer Alex Burlacu. Actually 
is a server providing SSE Stream with Tweeter tweets in JSON format. It can be pulled by 
using command:
```shell
docker pull alexburlacu/rtp-server:faf18x
```
To run the Docker Image, you can use next line:
```shell
docker run -p 4000:4000 alexburlacu/rtp-server:faf18x
```

# Used Technologies 

- ### Scala Language - 2.13.0

- ### Akka Library - 2.6.18

- ### Alpakka SSE - 3.0.4

- ### JSON4s Jackson - 4.1.0-M1