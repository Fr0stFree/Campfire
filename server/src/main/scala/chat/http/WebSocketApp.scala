package chat.http

import cats.effect.IO
import fs2.{Pipe, Stream}
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.Response
import org.http4s.dsl.io._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger

import chat.model.{ClientCommand, User}
import chat.service.{ChatService, UserJoinError, UserSession}

final class WebSocketApp(wsb: WebSocketBuilder2[IO], chat: ChatService)(using logger: Logger[IO]):

  def connect(user: User): IO[Response[IO]] = chat.join(user).foldF(
    error => handleConnectionError(user, error),
    session => wsb.build(handleSending(session), handleReceiving(session))
  )

  private def handleConnectionError(user: User, error: UserJoinError): IO[Response[IO]] =
    error match
      case UserJoinError.UsernameTaken(username) =>
        Conflict(s"Username '${username}' is already taken")
      case UserJoinError.InvalidUsername(username) => BadRequest(
          s"Invalid username '${username}'. Username must be 3-20 alphanumeric characters."
        )

  private def handleSending(session: UserSession): Stream[IO, WebSocketFrame] = Stream
    .fromQueueUnterminated(session.outgoing)
    .map(event => WebSocketFrame.Text(event.asJson.noSpaces)).onFinalize(chat.leave(session))

  private def handleReceiving(session: UserSession): Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
    case WebSocketFrame.Text(payload, _) => handlePayload(session, payload)
    case _                               => IO.unit
  }

  private def handlePayload(session: UserSession, payload: String): IO[Unit] =
    decode[ClientCommand](payload) match
      case Right(command) => chat.handle(session, command)
      case Left(error)    => logger
          .warn(s"Invalid command from ${session.user.name}: ${error.getMessage}")
