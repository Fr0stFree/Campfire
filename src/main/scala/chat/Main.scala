package chat

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import fs2.{Pipe, Stream}
import fs2.concurrent.Topic
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import chat.model.{ChatEvent, User}
import chat.http.{WebSocketChat, Router}
import io.circe.syntax.*
import chat.service.ChatService
import cats.effect.Ref

object Main extends IOApp.Simple {
  private def httpApp(chat: ChatService)(
      wsb: WebSocketBuilder2[IO]
  ): HttpApp[IO] = {
    val ws = new WebSocketChat(wsb, chat)
    new Router(chat, ws).routes.orNotFound
  }

  private def runServer(chat: ChatService): IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpWebSocketApp(httpApp(chat))
      .build
      .useForever

  override def run: IO[Unit] =
    for {
      chat <- ChatService.build
      _ <- runServer(chat)
    } yield ()
}
