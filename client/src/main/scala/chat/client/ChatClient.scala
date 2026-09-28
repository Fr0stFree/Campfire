package chat.client

import cats.effect.{Deferred, IO}
import cats.syntax.all.*
import fs2.Stream
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.client.websocket.{WSConnection, WSFrame}

import scala.concurrent.duration.*

import chat.model.ChatEvent

final class ChatClient(
    connection: WSConnection[IO],
    onEvent: ChatEvent => IO[Unit]
) {

  def run(commands: Stream[IO, ConsoleCommand]): IO[Unit] =
    Deferred[IO, Unit].flatMap { closeReceived =>
      IO.race(
        sendMessages(commands, closeReceived),
        receiveEvents(closeReceived)
      ).void
    }

  private def sendMessages(
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
      case Left(error) =>
        IO.println(
          s"Failed to decode server event: ${error.getMessage}\n$payload"
        )
    }
}
