# **Campfire**

Campfire is a real-time chat application written in Scala 3. It uses WebSockets
for bidirectional communication and is built around the functional programming
ecosystem of Cats Effect, FS2, and http4s.

The project is primarily intended for learning and experimenting with functional
effects, streaming, concurrency, and network programming in Scala.

## **Features**

- Real-time messaging over WebSockets.
- Multiple concurrent users.
- User join and leave events.
- JSON-based communication.
- In-memory user and chat state.
- Concurrent event distribution with FS2.
- Shared models and JSON codecs for the server and client.
- Command-line client module.

## **Tech Stack**

- Scala 3
- Cats Effect
- FS2
- http4s
- Circe
- sbt

## **Project Structure**

The project is split into three sbt modules:

```text
campfire/
├── build.sbt
├── server/
│   └── src/main/scala/...
├── client/
│   └── src/main/scala/...
└── shared/
    └── src/main/scala/...
```

- `server` contains the JVM application, HTTP routes, WebSocket handling, and
  chat service.
- `client` contains the command-line client.
- `shared` contains models and JSON codecs used by both the server and client.

## **Running**

Requirements:

- JDK 17+
- sbt

Clone the repository and run the server:

```bash
git clone <repository-url>
cd campfire
sbt server/run
```

By default, the server is available at:

```text
http://127.0.0.1:8080
```

The WebSocket endpoint is:

```text
ws://127.0.0.1:8080/ws/:username
```

For example, using `websocat`:

```bash
websocat ws://127.0.0.1:8080/ws/alice
```

Open another connection with a different username to start chatting.

To run the command-line client:

```bash
sbt client/run
```

## **Architecture**

Campfire follows a small layered architecture:

```text
CLI Client
       |
       | WebSocket / JSON
       v
HTTP / WebSocket Router
       |
       v
  Chat Service
       |
       v
Application State

Server <---- Shared models ----> Client
```

Cats Effect is used for effect management and concurrency, while FS2 streams
handle asynchronous WebSocket communication and event distribution. Circe
provides the shared JSON protocol used by the server and CLI client.

The application keeps its state in memory, making it intentionally lightweight
and focused on the core networking and functional-programming concepts.

## **Development**

Compile all modules:

```bash
sbt compile
```

Run the server:

```bash
sbt server/run
```

Run the command-line client:

```bash
sbt client/run
```

Run tests:

```bash
sbt test
```

Format the code:

```bash
sbt scalafmtAll
```

Check formatting:

```bash
sbt scalafmtCheckAll
```

## **Project Goals**

Campfire is an educational project focused on practical experience with:

- Scala 3 and its type system.
- Functional effect management with Cats Effect.
- Streaming with FS2.
- HTTP and WebSocket programming with http4s.
- Concurrent state management.
- Reusing models and protocol code between the server and client.
- Designing a small client-server application.

## **License**

This project is intended for educational purposes.
