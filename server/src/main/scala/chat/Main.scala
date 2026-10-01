package chat

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import chat.http.{WebSocketApp, HttpWsApp}
import chat.storage.memory.UserSessionStorage
import chat.service.ChatService

object Main extends IOApp.Simple {
  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private def runServer(service: ChatService): IO[Unit] = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpWebSocketApp(wsb => HttpWsApp.build(service, wsb))
      .build
      .useForever
  }

  override def run: IO[Unit] =
    for {
      _ <- Logger[IO].info("Starting Campfire server on 127.0.0.1:8080")
      sessionStorage <- UserSessionStorage.build
      service <- ChatService.build(sessionStorage)
      _ <- runServer(service)
    } yield ()
}
