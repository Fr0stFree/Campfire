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
import io.circe.syntax.*
import chat.service.{ChatService, ChatServiceImpl}

class Router(wsb: WebSocketBuilder2[IO], chat: ChatService) {
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health"        => Ok("ok")
    case GET -> Root / "ws" / username => websocket(User(username))
  }

  private def websocket(user: User): IO[Response[IO]] =
    val send: Stream[IO, WebSocketFrame] =
      Stream
        .eval(chat.join(user))
        .drain ++
        chat.subscribe
          .map(event => WebSocketFrame.Text(event.asJson.noSpaces))
          .onFinalize(chat.leave(user))

    val receive: Pipe[IO, WebSocketFrame, Unit] =
      _.evalMap {
        case WebSocketFrame.Text(msg, _) =>
          IO.println(s"Received message from ${user.name}: $msg") *> chat
            .send(user, msg)
            .void

        case _ => IO.unit
      }
    wsb.build(send, receive)
}

object Main extends IOApp.Simple {

  private def httpApp(chat: ChatService)(
      wsb: WebSocketBuilder2[IO]
  ): HttpApp[IO] =
    new Router(wsb, chat).routes.orNotFound

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
      topic <- Topic[IO, ChatEvent]
      chat = ChatServiceImpl(topic)
      _ <- runServer(chat)
    } yield ()
}
