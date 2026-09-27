package chat.client

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import fs2.io.stdinUtf8
import fs2.text
import io.circe.parser.decode
import org.http4s.Uri
import org.http4s.client.websocket.{WSConnectionHighLevel, WSFrame, WSRequest}
import org.http4s.jdkhttpclient.JdkWSClient
import fs2.Stream
import chat.model.{ChatEvent, User}

final class ChatClient(
    connection: WSConnectionHighLevel[IO],
    onEvent: ChatEvent => IO[Unit]
) {

  def run(commands: Stream[IO, ClientCommand]): IO[Unit] = {
    IO.race(sendMessages(commands), receiveEvents).void
  }
  private def sendMessages(commands: Stream[IO, ClientCommand]): IO[Unit] =
    commands
      .takeWhile(_ != ClientCommand.Quit)
      .collect { case ClientCommand.SendMessage(text) => text }
      .evalMap(text => connection.send(WSFrame.Text(text)))
      .compile
      .drain

  private def receiveEvents: IO[Unit] =
    connection.receiveStream
      .collect { case WSFrame.Text(payload, _) => payload }
      .evalMap(handlePayload)
      .compile
      .drain

  private def handlePayload(payload: String): IO[Unit] =
    decode[ChatEvent](payload) match {
      case Right(event) => onEvent(event)
      case Left(_)      => IO.println(s"[server] $payload")
    }
}
