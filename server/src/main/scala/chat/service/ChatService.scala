package chat.service

import cats.data.EitherT
import cats.effect.IO
import org.typelevel.log4cats.Logger

import chat.model.{ClientCommand, User}
import chat.storage.Storage

trait ChatService:
  def join(user: User): EitherT[IO, UserJoinError, UserSession]
  def leave(session: UserSession): IO[Unit]
  def handle(session: UserSession, command: ClientCommand): IO[Unit]

object ChatService:

  def build(sessions: Storage.UserSessions, events: Storage.ChatEvents)(using
    logger: Logger[IO]
  ): ChatService = new ChatServiceImpl(sessions, events)
