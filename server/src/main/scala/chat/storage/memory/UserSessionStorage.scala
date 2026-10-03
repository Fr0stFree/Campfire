package chat.storage.memory

import cats.data.EitherT
import cats.effect.{IO, Ref}

import chat.service.UserSession
import chat.storage.{Storage, StorageError}

final class UserSessionStorage(sessions: Ref[IO, Map[String, UserSession]])
    extends Storage.UserSessions:

  override def create(session: UserSession): EitherT[IO, StorageError.ObjectAlreadyExists, Unit] =
    EitherT {
      sessions.modify { current =>
        if current.contains(session.user.name) then
          (current, Left(StorageError.ObjectAlreadyExists(session.user.name)))
        else (current + (session.user.name -> session), Right(()))
      }
    }

  override def get(username: String): EitherT[IO, StorageError.ObjectDoesNotExist, UserSession] =
    EitherT {
      sessions.get
        .map(current => current.get(username).toRight(StorageError.ObjectDoesNotExist(username)))
    }

  override def delete(session: UserSession): EitherT[IO, StorageError.ObjectDoesNotExist, Unit] =
    EitherT {
      sessions.modify { current =>
        val username = session.user.name

        if current.get(username).contains(session) then (current.removed(username), Right(()))
        else (current, Left(StorageError.ObjectDoesNotExist(username)))
      }
    }

  override def list: IO[Seq[UserSession]] = sessions.get.map(_.values.toSeq)

object UserSessionStorage:

  def build: IO[UserSessionStorage] = for {
    sessions <- Ref.of[IO, Map[String, UserSession]](Map.empty)
  } yield new UserSessionStorage(sessions)
