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
}

final class ChatServiceImpl(topic: Topic[IO, ChatEvent]) extends ChatService {
  private val queueSize: Int = 10

  override def send(user: User, message: String): IO[Unit] =
    val event = ChatEvent.Message(user, message)
    topic.publish1(event).void

  override def subscribe: Stream[IO, ChatEvent] =
    topic.subscribe(queueSize)

  override def join(user: User): IO[Unit] =
    val event = ChatEvent.UserJoined(user)
    topic.publish1(event).void

  override def leave(user: User): IO[Unit] =
    val event = ChatEvent.UserLeft(user)
    topic.publish1(event).void
}
