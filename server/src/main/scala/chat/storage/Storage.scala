package chat.storage

import cats.data.EitherT
import cats.effect.IO

import chat.model.{ChatEvent, User}
import chat.service.UserSession

final case class ChatEventFilter(user: Option[User] = None, limit: Option[Int] = None)

object ChatEventFilter:
  def apply(user: User, limit: Int): ChatEventFilter = ChatEventFilter(Some(user), Some(limit))

object Storage:

  trait UserSessions:
    def create(session: UserSession): EitherT[IO, StorageError.ObjectAlreadyExists, Unit]

    def get(username: String): EitherT[IO, StorageError.ObjectDoesNotExist, UserSession]

    def delete(session: UserSession): EitherT[IO, StorageError.ObjectDoesNotExist, Unit]

    def list: IO[Seq[UserSession]]

  trait ChatEvents:
    def save(event: ChatEvent): IO[Unit]
    def list(filter: ChatEventFilter): IO[Seq[ChatEvent]]
