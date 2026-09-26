package chat.http

import cats.effect.IO
import fs2.{Pipe, Stream}
import io.circe.syntax.*
import org.http4s.Response
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import chat.model.{UserConnectResult, User}
import chat.service.ChatService

final class WebSocketChat(
    wsb: WebSocketBuilder2[IO],
    chat: ChatService
) {
  def connect(user: User): IO[Response[IO]] =
    chat
      .join(user)
      .flatMap {
        case UserConnectResult.Connected     => build(user)
        case UserConnectResult.UsernameTaken =>
          Conflict(s"Username '${user.name}' is already taken")
      }

  private def build(user: User): IO[Response[IO]] = {
    val send: Stream[IO, WebSocketFrame] =
      chat.subscribe
        .map { event =>
          WebSocketFrame.Text(event.asJson.noSpaces)
        }
        .onFinalize(chat.leave(user))

    val receive: Pipe[IO, WebSocketFrame, Unit] =
      _.evalMap {
        case WebSocketFrame.Text(message, _) => chat.send(user, message)
        case _                               => IO.unit
      }

    wsb.build(send, receive)
  }
}
