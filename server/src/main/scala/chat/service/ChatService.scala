package chat.service
import cats.effect.IO
import chat.model.{ChatEvent, User, UserConnectResult}
import fs2.Stream
import fs2.concurrent.Topic
import cats.effect.Ref

trait ChatService {
  def send(user: User, message: String): IO[Unit]
  def subscribe: Stream[IO, ChatEvent]
  def join(user: User): IO[UserConnectResult]
  def leave(user: User): IO[Unit]
  def getUsers(): IO[Set[User]]
}

object ChatService {
  def build: IO[ChatService] =
    for {
      storage <- Ref.of[IO, Set[User]](Set.empty)
      topic <- Topic[IO, ChatEvent]
    } yield ChatServiceImpl(topic, storage)
}

final class ChatServiceImpl(
    topic: Topic[IO, ChatEvent],
    users: Ref[IO, Set[User]]
) extends ChatService {
  private val queueSize: Int = 10

  override def send(user: User, message: String): IO[Unit] =
    topic
      .publish1(ChatEvent.Message(user, message))
      .void

  override def subscribe: Stream[IO, ChatEvent] =
    topic.subscribe(queueSize)

  override def join(user: User): IO[UserConnectResult] =
    users
      .modify(users =>
        if (users.contains(user))
        then (users, UserConnectResult.UsernameTaken)
        else (users.incl(user), UserConnectResult.Connected)
      )
      .flatTap {
        case UserConnectResult.Connected =>
          topic.publish1(ChatEvent.UserJoined(user)).void
        case UserConnectResult.UsernameTaken => IO.unit
      }

  override def leave(user: User): IO[Unit] =
    users
      .update(_.excl(user)) *> topic
      .publish1(ChatEvent.UserLeft(user))
      .void

  override def getUsers(): IO[Set[User]] = users.get
}
