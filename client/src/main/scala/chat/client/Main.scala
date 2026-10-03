package chat.client

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.Uri
import org.http4s.client.websocket.WSRequest
import org.http4s.jdkhttpclient.JdkWSClient

import chat.client.console.{Console, ConsoleEventProcessor, ConsoleInput}
import chat.client.transport.ChatClient

object Main extends IOApp:
  private val errorMessage = "Usage: campfire-client <username>"
  private val greetingMessage = "Welcome to Campfire! Type a message and press Enter; /quit exits."
  private val serverUri = Uri.unsafeFromString("ws://127.0.0.1:8080")

  override def run(args: List[String]): IO[ExitCode] = retrieveUsername(args) match
    case Some(username) => connect(username).as(ExitCode.Success).handleErrorWith { error =>
        IO.println(s"Connection failed: ${error.getMessage}").as(ExitCode.Error)
      }

    case None => IO.println(errorMessage).as(ExitCode.Error)

  private def connect(username: String): IO[Unit] = Console.resource.use { console =>
    console.printLine(greetingMessage) *> JdkWSClient.simple[IO].use { wsClient =>
      val uri = serverUri / "ws" / username
      wsClient.connect(WSRequest(uri)).use { connection =>
        val processor = ConsoleEventProcessor(username, console)
        ChatClient(connection, processor.process, console.printLine)
          .run(ConsoleInput.commands(console)).guarantee(console.printLine("Disconnected."))
      }
    }
  }

  private def retrieveUsername(args: List[String]): Option[String] = args match
    case username :: Nil if username.trim.nonEmpty => Some(username.trim)
    case _                                         => None
