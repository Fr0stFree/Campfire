package chat

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import chat.http.{WebSocketApp, HttpWsApp}
import chat.storage.memory.{UserSessionStorage, ChatEventStorage}
import chat.service.ChatService

object Main extends IOApp.Simple {
  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private val port = port"8080"
  private val host = ipv4"127.0.0.1" // TODO: hide in config

  private def runServer(service: ChatService): IO[Unit] = {
    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpWebSocketApp(wsb => HttpWsApp.build(service, wsb))
      .build
      .useForever
  }

  override def run: IO[Unit] =
    for {
      _ <- Logger[IO].info(s"Starting Campfire server on $host:$port")
      sessionStorage <- UserSessionStorage.build
      chatEventStorage <- ChatEventStorage.build
      service = ChatService.build(sessionStorage, chatEventStorage)
      _ <- runServer(service)
    } yield ()
}
