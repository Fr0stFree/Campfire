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
import chat.model.ChatMessage
import io.circe.syntax.*

class Router(wsb: WebSocketBuilder2[IO], topic: Topic[IO, ChatMessage]) {
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health"        => Ok("ok")
    case GET -> Root / "ws" / username => websocket(username)
  }

  private def websocket(username: String): IO[Response[IO]] =
    val send: Stream[IO, WebSocketFrame] =
      topic
        .subscribe(10)
        .map(message => WebSocketFrame.Text(message.asJson.noSpaces))

    val receive: Pipe[IO, WebSocketFrame, Unit] =
      _.evalMap {
        case WebSocketFrame.Text(msg, _) =>
          IO.println(s"Received message from $username: $msg") *> topic
            .publish1(ChatMessage(username, msg))
            .void

        case _ => IO.unit
      }
    wsb.build(send, receive)
}

object Main extends IOApp.Simple {

  private def httpApp(topic: Topic[IO, ChatMessage])(
      wsb: WebSocketBuilder2[IO]
  ): HttpApp[IO] =
    new Router(wsb, topic).routes.orNotFound

  private def runServer(topic: Topic[IO, ChatMessage]): IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpWebSocketApp(httpApp(topic))
      .build
      .useForever

  override def run: IO[Unit] =
    for {
      topic <- Topic[IO, ChatMessage]
      _ <- runServer(topic)
    } yield ()
}
