package chat.service

import cats.data.EitherT
import cats.effect.{IO, Ref}
import chat.model.{ClientCommand, User, UserJoinError, UserSession}
import org.typelevel.log4cats.Logger

trait ChatService {
  def join(user: User): EitherT[IO, UserJoinError, UserSession]
  def leave(session: UserSession): IO[Unit]
  def handle(session: UserSession, command: ClientCommand): IO[Unit]
}

object ChatService {

  def build(using Logger[IO]): IO[ChatService] =
    Ref
      .of[IO, Map[String, UserSession]](Map.empty)
      .map(new ChatServiceImpl(_))
}
