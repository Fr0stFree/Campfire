package chat.storage

import cats.data.EitherT
import cats.effect.IO
import chat.service.UserSession

object Storage {
  trait UserSessions {
    def create(
        session: UserSession
    ): EitherT[IO, StorageError.UserSessionAlreadyExists, Unit]

    def get(
        username: String
    ): EitherT[IO, StorageError.UserSessionNotFound, UserSession]

    def delete(
        session: UserSession
    ): EitherT[IO, StorageError.UserSessionNotFound, Unit]

    def list: IO[Seq[UserSession]]
  }
}
