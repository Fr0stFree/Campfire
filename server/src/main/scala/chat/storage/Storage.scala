package chat.storage

import cats.data.EitherT
import cats.effect.IO
import chat.service.UserSession
import chat.model.ChatEvent

object Storage {
  trait UserSessions {
    def create(
        session: UserSession
    ): EitherT[IO, StorageError.ObjectAlreadyExists, Unit]

    def get(
        username: String
    ): EitherT[IO, StorageError.ObjectDoesNotExist, UserSession]

    def delete(
        session: UserSession
    ): EitherT[IO, StorageError.ObjectDoesNotExist, Unit]

    def list: IO[Seq[UserSession]]
  }

  trait ChatEvents {
    def save(event: ChatEvent): IO[Unit]
  }
}
