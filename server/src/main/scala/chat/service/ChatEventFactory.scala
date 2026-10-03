package chat.service

import cats.Monad
import cats.effect.std.UUIDGen
import cats.effect.Clock
import cats.syntax.all.*
import chat.model.{ChatEvent, User}

import java.time.Instant
import java.util.UUID

final class ChatEventFactory[F[_]: Monad](using
    clock: Clock[F],
    uuidGen: UUIDGen[F]
) {
  private def create[A](
      build: (UUID, Instant) => A
  ): F[A] =
    (uuidGen.randomUUID, clock.realTimeInstant).mapN(build)

  def userJoined(user: User): F[ChatEvent.UserJoined] =
    create(ChatEvent.UserJoined(_, user, _))

  def userLeft(user: User): F[ChatEvent.UserLeft] =
    create(ChatEvent.UserLeft(_, user, _))

  def broadcast(
      sender: User,
      message: String
  ): F[ChatEvent.Broadcast] =
    create(ChatEvent.Broadcast(_, sender, message, _))

  def directMessage(
      sender: User,
      recipient: User,
      message: String
  ): F[ChatEvent.DirectMessage] =
    create(ChatEvent.DirectMessage(_, sender, recipient, message, _))

  def usersListed(users: Seq[User]): F[ChatEvent.UsersListed] =
    create(ChatEvent.UsersListed(_, users, _))

  def messageRejected(reason: String): F[ChatEvent.MessageRejected] =
    create(ChatEvent.MessageRejected(_, _, reason))

  def messageAccepted: F[ChatEvent.MessageAccepted] =
    create(ChatEvent.MessageAccepted.apply)
}
