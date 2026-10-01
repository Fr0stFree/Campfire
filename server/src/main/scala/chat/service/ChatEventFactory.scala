package chat.service

import cats.data.EitherT
import cats.effect.std.UUIDGen
import cats.effect.{Clock, IO}
import cats.syntax.all.*
import chat.model.{ChatEvent, ClientCommand, User}
import chat.storage.{Storage, StorageError}
import org.typelevel.log4cats.Logger
import java.time.Instant
import java.util.UUID

import chat.model.{ClientCommand, User}
import chat.storage.Storage

final class ChatEventFactory(
    idProvider: IO[UUID] = UUIDGen.randomUUID[IO],
    timeProvider: IO[Instant] = Clock[IO].realTimeInstant
) {

  def userJoined(user: User): IO[ChatEvent.UserJoined] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.UserJoined(id, user, time)

  def userLeft(user: User): IO[ChatEvent.UserLeft] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.UserLeft(id, user, time)

  def broadcast(
      sender: User,
      message: String
  ): IO[ChatEvent.Broadcast] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.Broadcast(id, sender, message, time)

  def directMessage(
      sender: UserSession,
      recipient: UserSession,
      message: String
  ): IO[ChatEvent.DirectMessage] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.DirectMessage(
      id,
      sender.user,
      recipient.user,
      message,
      time
    )
  def usersListed(
      destination: UserSession,
      users: Seq[User]
  ): IO[ChatEvent.UsersListed] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.UsersListed(id, users, time)

  def messageRejected(
      destination: UserSession,
      reason: String
  ): IO[ChatEvent.MessageRejected] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.MessageRejected(id, time, reason)

  def messageAccepted(
      destination: UserSession
  ): IO[ChatEvent.MessageAccepted] =
    for {
      id <- idProvider
      time <- timeProvider
    } yield ChatEvent.MessageAccepted(id, time)
}
