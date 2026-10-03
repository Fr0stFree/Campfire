package chat

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import chat.config.AppConfig
import chat.http.HttpWsApp
import chat.service.ChatService
import chat.storage.memory.{ChatEventStorage, UserSessionStorage}

object Main extends IOApp:
  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private def runServer(config: AppConfig, service: ChatService): IO[Unit] = EmberServerBuilder
    .default[IO].withHost(config.host).withPort(config.port)
    .withHttpWebSocketApp(wsb => HttpWsApp.build(service, wsb)).build.useForever

  override def run(args: List[String]): IO[ExitCode] = AppConfig.fromArgs(args) match
    case Left(error)   => Logger[IO].error(error)(error.getMessage).as(ExitCode.Error)
    case Right(config) => for {
        _ <- Logger[IO].info(s"Starting Campfire server on ${config.host}:${config.port}")
        sessionStorage <- UserSessionStorage.build
        chatEventStorage <- ChatEventStorage.build
        service = ChatService.build(sessionStorage, chatEventStorage)
        _ <- runServer(config, service)
      } yield ExitCode.Success
