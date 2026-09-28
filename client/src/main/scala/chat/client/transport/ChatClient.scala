package chat.client.transport

import cats.effect.{Deferred, IO}
import cats.syntax.all.*
import chat.client.console.ConsoleCommand
import chat.model.ChatEvent
import fs2.Stream
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.client.websocket.{WSConnection, WSFrame}

import scala.concurrent.duration.*

final class ChatClient(
    connection: WSConnection[IO],
    onEvent: ChatEvent => IO[Unit],
    onInvalidEvent: String => IO[Unit]
) {

  def run(commands: Stream[IO, ConsoleCommand]): IO[Unit] =
    Deferred[IO, Unit].flatMap { closeReceived =>
      IO.race(
        sendCommands(commands, closeReceived),
        receiveEvents(closeReceived)
      ).void
    }

  private def sendCommands(
      commands: Stream[IO, ConsoleCommand],
      closeReceived: Deferred[IO, Unit]
  ): IO[Unit] =
    commands
      .takeWhile(_ != ConsoleCommand.Quit)
      .collect { case ConsoleCommand.Send(command) => command }
      .evalMap(command =>
        connection.send(WSFrame.Text(command.asJson.noSpaces))
      )
      .compile
      .drain *>
      connection.send(WSFrame.Close(1000, "Client quit")) *>
      closeReceived.get.timeoutTo(2.seconds, IO.unit)

  private def receiveEvents(closeReceived: Deferred[IO, Unit]): IO[Unit] =
    connection.receiveStream
      .evalMap {
        case WSFrame.Text(payload, _) => handlePayload(payload)
        case _: WSFrame.Close         => closeReceived.complete(()).void
        case _                        => IO.unit
      }
      .compile
      .drain

  private def handlePayload(payload: String): IO[Unit] =
    decode[ChatEvent](payload) match {
      case Right(event) => onEvent(event)
      case Left(error)  =>
        onInvalidEvent(s"Failed to decode event: ${error.getMessage}\n$payload")
    }
}
