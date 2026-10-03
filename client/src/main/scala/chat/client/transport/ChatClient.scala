package chat.client.transport

import scala.concurrent.duration.*

import cats.effect.{Deferred, IO}
import fs2.Stream
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.client.websocket.{WSConnection, WSFrame}
import scodec.bits.ByteVector

import chat.client.console.ConsoleCommand
import chat.model.ChatEvent

final class ChatClient(
  connection: WSConnection[IO],
  onEvent: ChatEvent => IO[Unit],
  onInvalidEvent: String => IO[Unit],
  heartbeatInterval: FiniteDuration = 30.seconds
):

  def run(commands: Stream[IO, ConsoleCommand]): IO[Unit] = Deferred[IO, Unit]
    .flatMap { closeReceived =>
      IO.race(sendUntilQuit(commands, closeReceived), receiveUntilClosed(closeReceived)).void
    }

  private def sendUntilQuit(
    commands: Stream[IO, ConsoleCommand],
    closeReceived: Deferred[IO, Unit]
  ): IO[Unit] =
    val commandFrames = commands.takeWhile(_ != ConsoleCommand.Quit)
      .collect { case ConsoleCommand.Send(command) => WSFrame.Text(command.asJson.noSpaces) }

    val heartbeatFrames = Stream.awakeEvery[IO](heartbeatInterval)
      .as(WSFrame.Ping(ByteVector.empty))

    commandFrames.mergeHaltL(heartbeatFrames).evalMap(connection.send).compile.drain *>
      connection.send(WSFrame.Close(1000, "Client quit")) *>
      closeReceived.get.timeoutTo(2.seconds, IO.unit)

  private def receiveUntilClosed(closeReceived: Deferred[IO, Unit]): IO[Unit] = connection
    .receiveStream.evalMap {
      case WSFrame.Text(payload, _) => handlePayload(payload)
      case _: WSFrame.Close         => closeReceived.complete(()).void
      case _                        => IO.unit
    }.compile.drain

  private def handlePayload(payload: String): IO[Unit] = decode[ChatEvent](payload) match
    case Right(event) => onEvent(event)
    case Left(error)  => onInvalidEvent(s"Failed to decode event: ${error.getMessage}\n$payload")
