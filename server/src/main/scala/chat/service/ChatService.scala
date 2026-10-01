package chat.service

import cats.data.EitherT
import cats.effect.IO
import chat.model.{ClientCommand, User}
import chat.storage.Storage
import org.typelevel.log4cats.Logger

trait ChatService {
  def join(user: User): EitherT[IO, UserJoinError, UserSession]
  def leave(session: UserSession): IO[Unit]
  def handle(session: UserSession, command: ClientCommand): IO[Unit]
}

object ChatService {
  def build(
      sessions: Storage.UserSessions
  )(using logger: Logger[IO]): IO[ChatService] =
    IO(new ChatServiceImpl(sessions))
}
