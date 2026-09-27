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

object ConsoleInput {
  val commands: Stream[IO, ConsoleCommand] =
    stdinUtf8[IO](4096)
      .through(text.lines)
      .map(ConsoleCommand.fromString)
      .unNone

}

object Main extends IOApp {
  private val errorMessage = "Usage: campfire-client <username>"
  private val greetingMessage =
    "Welcome to Campfire! Type a message and press Enter; /quit exits."
  private val serverUri = Uri.unsafeFromString("ws://127.0.0.1:8080")

  override def run(args: List[String]): IO[ExitCode] =
    retrieveUsername(args) match {
      case Some(username) =>
        connect(username)
          .as(ExitCode.Success)
          .handleErrorWith { error =>
            IO.println(s"Connection failed: ${error.getMessage}")
              .as(ExitCode.Error)
          }

      case None => IO.println(errorMessage).as(ExitCode.Error)
    }

  private def connect(username: String): IO[Unit] =
    JdkWSClient.simple[IO].use { wsClient =>
      val uri = serverUri / "ws" / username
      wsClient
        .connect(WSRequest(uri))
        .use { connection =>
          val processor = ConsoleEventProcessor(username)
          ChatClient(connection, processor.process)
            .run(ConsoleInput.commands)
            .guarantee(IO.println("Disconnected."))
        }
    }

  private def retrieveUsername(args: List[String]): Option[String] =
    args match {
      case username :: Nil if username.trim.nonEmpty => Some(username.trim)
      case _                                         => None
    }
}
