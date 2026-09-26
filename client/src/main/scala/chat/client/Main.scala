package chat.client

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import fs2.io.stdinUtf8
import fs2.text
import io.circe.parser.decode
import org.http4s.Uri
import org.http4s.client.websocket.{WSConnectionHighLevel, WSFrame, WSRequest}
import org.http4s.jdkhttpclient.JdkWSClient

import chat.model.ChatEvent

object Main extends IOApp {

  private val serverUri = Uri.unsafeFromString("ws://127.0.0.1:8080")

  private def retrieveUsername(args: List[String]): Option[String] =
    args match {
      case username :: Nil if username.trim.nonEmpty => Some(username.trim)
      case _                                         => None
    }

  override def run(args: List[String]): IO[ExitCode] =
    retrieveUsername(args) match {
      case Some(username) =>
        runClient(username)
          .as(ExitCode.Success)
          .handleErrorWith { error =>
            IO.println(s"Connection failed: ${error.getMessage}")
              .as(ExitCode.Error)
          }

      case _ =>
        IO.println("Usage: campfire-client <username>")
          .as(ExitCode.Error)
    }

  private def runClient(username: String): IO[Unit] =
    JdkWSClient.simple[IO].use { client =>
      val uri = serverUri / "ws" / username

      IO.println(s"Connecting to $uri ...") *>
        client.connectHighLevel(WSRequest(uri)).use { connection =>
          IO.println(
            "Connected. Type a message and press Enter; /quit exits."
          ) *>
            IO.race(
              sendMessages(connection),
              receiveEvents(connection, username)
            ).void
        }
    }

  private def sendMessages(connection: WSConnectionHighLevel[IO]): IO[Unit] =
    stdinUtf8[IO](4096)
      .through(text.lines)
      .takeWhile(_ != "/quit")
      .filter(_.nonEmpty)
      .evalMap(message => connection.send(WSFrame.Text(message)))
      .compile
      .drain

  private def receiveEvents(
      connection: WSConnectionHighLevel[IO],
      username: String
  ): IO[Unit] =
    connection.receiveStream
      .collect { case WSFrame.Text(payload, _) => payload }
      .evalMap(payload => render(payload, username).traverse_(IO.println))
      .compile
      .drain

  private def render(payload: String, username: String): Option[String] =
    decode[ChatEvent](payload).fold(
      _ => Some(s"[server] $payload"),
      {
        case ChatEvent.UserJoined(user) => Some(s"* ${user.name} joined")
        case ChatEvent.UserLeft(user)   => Some(s"* ${user.name} left")
        case ChatEvent.Message(user, _) if user.name == username => None
        case ChatEvent.Message(user, text) => Some(s"${user.name}: $text")
      }
    )
}
