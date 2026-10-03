package chat

import cats.effect.{IO, IOApp, ExitCode}
import com.comcast.ip4s.*
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import chat.http.{WebSocketApp, HttpWsApp}
import chat.storage.memory.{UserSessionStorage, ChatEventStorage}
import chat.service.ChatService
import chat.config.AppConfig

object Main extends IOApp {
  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private def runServer(config: AppConfig, service: ChatService): IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpWebSocketApp(wsb => HttpWsApp.build(service, wsb))
      .build
      .useForever

  override def run(args: List[String]): IO[ExitCode] =
    AppConfig.fromArgs(args) match {
      case Left(error) =>
        Logger[IO].error(error)(error.getMessage).as(ExitCode.Error)
      case Right(config) =>
        for {
          _ <- Logger[IO].info(
            s"Starting Campfire server on ${config.host}:${config.port}"
          )
          sessionStorage <- UserSessionStorage.build
          chatEventStorage <- ChatEventStorage.build
          service = ChatService.build(sessionStorage, chatEventStorage)
          _ <- runServer(config, service)
        } yield ExitCode.Success
    }
}
