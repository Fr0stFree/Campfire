package chat.http

import cats.effect.IO
import fs2.{Pipe, Stream}
import io.circe.syntax.*
import org.http4s.Response
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import chat.model.{UserConnectResult, User, ClientCommand}
import chat.service.ChatService
import org.typelevel.log4cats.Logger
import io.circe.parser.decode
import cats.effect.Clock

final class WebSocketChat(
    wsb: WebSocketBuilder2[IO],
    chat: ChatService
)(using logger: Logger[IO]) {

  def connect(user: User): IO[Response[IO]] = {
    chat
      .join(user)
      .flatMap {
        case UserConnectResult.Connected =>
          wsb.build(handleSending(user), handleReceiving(user))
        case UserConnectResult.UsernameTaken =>
          Conflict(s"Username '${user.name}' is already taken")
      }
  }

  private def handleSending(user: User) = {
    chat.subscribe
      .map(event => WebSocketFrame.Text(event.asJson.noSpaces))
      .onFinalize(chat.leave(user))
  }

  private def handleReceiving(user: User): Pipe[IO, WebSocketFrame, Unit] = {
    _.evalMap {
      case WebSocketFrame.Text(payload, _) => handlePayload(user, payload)
      case _                               => IO.unit
    }
  }

  private def handlePayload(user: User, payload: String): IO[Unit] = {
    decode[ClientCommand](payload) match {
      case Right(command) => chat.handle(user, command)
      case Left(error)    =>
        logger.warn(s"Invalid command from ${user.name}: ${error.getMessage}")
    }
  }
}
