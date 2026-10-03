# Campfire

Campfire is a lightweight real-time chat written in Scala 3. The project consists of an
http4s WebSocket server, an interactive command-line client, and a shared JSON protocol.
It is built as an educational project for exploring functional effects, streams, concurrency,
and client-server communication with Cats Effect and FS2.

## Features

- Real-time group chat over WebSockets.
- Direct messages between connected users.
- Join and leave notifications.
- A command for listing users currently online.
- The 10 most recent relevant messages are shown when a user connects.
- Shared, type-safe commands and events encoded as JSON with Circe.
- In-memory, concurrency-safe session and event storage.
- Interactive terminal client with timestamps and colored output.
- Health-check endpoint for the server.

All state is stored in memory and is cleared when the server stops.

## Tech Stack

- Scala `3.9.0`
- sbt `1.12.11`
- Cats Effect `3.7.1`
- FS2
- http4s `0.23.37`
- Circe `0.14.14`
- JLine `3.26.0`
- Log4cats and Logback
- MUnit

## Requirements

- JDK 17 or newer
- sbt
- `make` (optional, for the provided shortcuts)

## Quick Start

Clone the repository and install the dependencies:

```bash
git clone https://github.com/Fr0stFree/Otus-Scala-Final.git
cd Otus-Scala-Final
make install
```

Start the server:

```bash
make run-server
```

In another terminal, start a client and choose a username:

```bash
make run-client ARGS=alice
```

Open one or more additional terminals with different usernames to start chatting:

```bash
make run-client ARGS=bob
```

Usernames must contain between 3 and 20 ASCII letters or digits. A username cannot be used by
more than one active connection.

## Client Commands

| Input | Description |
| --- | --- |
| `<message>` | Send a message to everyone in the chat |
| `/msg <username> <message>` | Send a direct message to a connected user |
| `/users` | Show the users currently online |
| `/quit` | Disconnect and exit |

Pressing `Ctrl-D` also disconnects the client. Empty input and unrecognized slash commands are
ignored.

## Server Configuration

The server accepts the port and host as optional positional arguments passed through `ARGS`:

```bash
make run-server ARGS='[port] [host]'
```

The defaults are:

| Setting | Default |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `8080` |
| WebSocket endpoint | `ws://127.0.0.1:8080/ws/:username` |
| Health endpoint | `http://127.0.0.1:8080/health` |

For example, to listen on every network interface on port `9000`:

```bash
make run-server ARGS='9000 0.0.0.0'
```

The bundled CLI client currently connects to `ws://127.0.0.1:8080`, so a server started with
different connection settings requires a corresponding change to the client URI.

Check that the default server is running:

```bash
curl http://127.0.0.1:8080/health
```

A healthy server responds with `200 OK` and the plain-text body `ok`.

## Usage Examples

Start the server in the first terminal:

```console
$ make run-server
```

Connect Alice in the second terminal:

```console
$ make run-client ARGS=alice
Welcome to Campfire! Type a message and press Enter; /quit exits.
>
```

Connect Bob in the third terminal:

```console
$ make run-client ARGS=bob
Welcome to Campfire! Type a message and press Enter; /quit exits.
>
```

Alice sees a notification when Bob connects:

```text
[12:00:01] ** bob joined **
```

### Public Messages

Enter plain text to send a message to everyone, including the sender:

```console
> Hello everyone!
[12:00:10] alice: Hello everyone!
```

Bob receives the same message:

```text
[12:00:10] alice: Hello everyone!
```

### Direct Messages

Use `/msg` to send a private message:

```console
> /msg bob Hi Bob!
[12:00:20] you -> bob: Hi Bob!
```

Only Alice and Bob see this message. Bob sees:

```text
[12:00:20] alice -> you: Hi Bob!
```

If the recipient is not connected, the sender receives an error:

```console
> /msg charlie Are you there?
[12:00:25] ** User 'charlie' not found **
```

### Online Users

Use `/users` to display the currently connected users:

```console
> /users
[12:00:30] Online: alice, bob
```

### Leaving the Chat

Use `/quit` or press `Ctrl-D` to disconnect:

```console
> /quit
Disconnected.
```

The other users receive a notification:

```text
[12:00:40] ** bob left **
```

### Message History

When a user reconnects, the client displays up to 10 recent messages available to that user.
Public messages are visible to everyone, while private messages are restored only for their sender
and recipient. Join and leave notifications are not included in the history.

## Project Structure

The build is split into three sbt modules:

```text
.
├── build.sbt
├── Makefile
├── client/
│   └── src/main/scala/chat/client/
├── server/
│   └── src/main/scala/chat/
└── shared/
    └── src/main/scala/chat/model/
```

- `server` contains the HTTP/WebSocket transport, chat service, and in-memory storage.
- `client` contains the interactive console and WebSocket client.
- `shared` contains the commands, events, users, and Circe codecs used on both sides.

## Architecture

```text
Terminal input
     |
     v
CLI client ---- WebSocket / JSON ---- HTTP/WebSocket routes
                                            |
                                            v
                                       Chat service
                                       /          \
                              User sessions    Event history

          client <----- shared protocol -----> server
```

Cats Effect manages resources and asynchronous effects. FS2 models console input and WebSocket
traffic as streams. Connected sessions use bounded queues for outgoing events, while `Ref`
provides concurrency-safe in-memory storage. The server broadcasts public events to all active
sessions and delivers direct messages only to their sender and recipient.

When a user joins, the server replays up to 10 recent messages visible to that user: all public
messages and direct messages where that user is either the sender or recipient. Join/leave events
are not included in this replay.

## Development

The most common commands are available through the `Makefile`:

| Command | Description |
| --- | --- |
| `make install` | Resolve and download sbt dependencies |
| `make build` | Compile all modules |
| `make run-server` | Start the server; pass options through `ARGS` |
| `make run-client ARGS=<username>` | Start the CLI client |
| `make test` | Run all tests |
| `make fmt` | Format Scala and sbt sources |
| `make fmt-check` | Check formatting without changing files |
| `make lint` | Run Scalafix checks |
| `make lint-fix` | Apply Scalafix rules |
| `make clean` | Remove build output |

Run all available checks separately:

```bash
make build
make test
make fmt-check
make lint
```

## Current Limitations

- Sessions and message history are not persisted.
- There is no authentication or transport encryption built into the application.
- The CLI server address is currently fixed at `127.0.0.1:8080`.
- Horizontal scaling is not supported because state is local to one server process.

## License

This project is intended for educational purposes. No license file is currently included.
