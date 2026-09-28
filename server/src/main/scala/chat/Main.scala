package chat

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import chat.http.{WebSocketChat, Router}
import chat.service.ChatService

object Main extends IOApp.Simple {
  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private def httpApp(chat: ChatService)(
      wsb: WebSocketBuilder2[IO]
  ): HttpApp[IO] = {
    val ws = new WebSocketChat(wsb, chat)
    new Router(chat, ws).routes.orNotFound
  }

  private def runServer(chat: ChatService): IO[Unit] = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpWebSocketApp(httpApp(chat))
      .build
      .useForever
  }

  override def run: IO[Unit] =
    for {
      chat <- ChatService.build
      _ <- Logger[IO].info("Starting Campfire server on 127.0.0.1:8080")
      _ <- runServer(chat)
    } yield ()
}
