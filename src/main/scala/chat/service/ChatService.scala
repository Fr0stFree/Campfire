package chat.service
import cats.effect.IO
import chat.model.{ChatEvent, User}
import fs2.Stream
import fs2.concurrent.Topic
import cats.effect.Ref

trait ChatService {
  def send(user: User, message: String): IO[Unit]
  def subscribe: Stream[IO, ChatEvent]
  def join(user: User): IO[Unit]
  def leave(user: User): IO[Unit]
  def users(): IO[Set[User]]
}

final class ChatServiceImpl(
    topic: Topic[IO, ChatEvent],
    userStorage: Ref[IO, Set[User]]
) extends ChatService {
  private val queueSize: Int = 10

  override def send(user: User, message: String): IO[Unit] =
    topic
      .publish1(ChatEvent.Message(user, message))
      .void

  override def subscribe: Stream[IO, ChatEvent] =
    topic.subscribe(queueSize)

  override def join(user: User): IO[Unit] =
    userStorage
      .update(_.incl(user)) *> topic
      .publish1(ChatEvent.UserJoined(user))
      .void

  override def leave(user: User): IO[Unit] =
    userStorage
      .update(_.excl(user)) *> topic
      .publish1(ChatEvent.UserLeft(user))
      .void

  override def users(): IO[Set[User]] = userStorage.get
}
