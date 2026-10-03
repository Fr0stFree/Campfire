package chat.service

import java.time.Instant

import cats.effect.IO
import cats.effect.std.Queue

import chat.model.{ChatEvent, User}

final case class UserSession(user: User, outgoing: Queue[IO, ChatEvent], connectedAt: Instant):
  def send(event: ChatEvent): IO[Unit] = outgoing.offer(event)

object UserSession:

  def create(user: User, queueSize: Int): IO[UserSession] =
    for
      queue <- Queue.bounded[IO, ChatEvent](queueSize)
      timestamp <- IO.realTimeInstant
    yield UserSession(user, queue, timestamp)
