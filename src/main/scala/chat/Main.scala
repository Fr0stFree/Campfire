package chat

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpRoutes
import fs2.Stream
import org.http4s.websocket.WebSocketFrame
import org.http4s.server.websocket.WebSocketBuilder2

class Router(wsb: WebSocketBuilder2[IO]) {
  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "health" => Ok("ok")
    case GET -> Root / "ws"     =>
      val send = Stream.empty
      val receive: fs2.Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
        case WebSocketFrame.Text(msg, _) => IO.println(s"Received: $msg")
        case _                           => IO.unit
      }
      wsb.build(send, receive)
  }
}

object Main extends IOApp.Simple {
  override def run: IO[Unit] = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpWebSocketApp(wsb => new Router(wsb).routes.orNotFound)
      .build
      .useForever
  }
}
