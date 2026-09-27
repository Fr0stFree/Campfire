package chat.service
import cats.effect.IO
import chat.model.{ChatEvent, User, UserConnectResult, ClientCommand}
import fs2.Stream
import fs2.concurrent.Topic
import cats.effect.Ref
import org.typelevel.log4cats.Logger

trait ChatService {
  def handle(user: User, command: ClientCommand): IO[Unit]
  def subscribe: Stream[IO, ChatEvent]
  def join(user: User): IO[UserConnectResult]
  def leave(user: User): IO[Unit]
}

object ChatService {
  def build(using Logger[IO]): IO[ChatService] =
    for {
      storage <- Ref.of[IO, Set[User]](Set.empty)
      topic <- Topic[IO, ChatEvent]
    } yield ChatServiceImpl(topic, storage)
}

final class ChatServiceImpl(
    topic: Topic[IO, ChatEvent],
    users: Ref[IO, Set[User]]
)(using logger: Logger[IO])
    extends ChatService {

  private val queueSize: Int = 10

  override def subscribe: Stream[IO, ChatEvent] = topic.subscribe(queueSize)

  override def join(user: User): IO[UserConnectResult] = {
    users
      .modify(users =>
        if (users.contains(user))
        then (users, UserConnectResult.UsernameTaken)
        else (users.incl(user), UserConnectResult.Connected)
      )
      .flatTap {
        case UserConnectResult.Connected =>
          topic
            .publish1(ChatEvent.UserJoined(user))
            .void *> logger.info(s"User ${user.name} joined the chat")
        case UserConnectResult.UsernameTaken => IO.unit
      }
  }

  override def leave(user: User): IO[Unit] =
    users
      .update(_.excl(user)) *> topic
      .publish1(ChatEvent.UserLeft(user))
      .void *> logger.info(s"User ${user.name} left the chat")

  override def handle(user: User, command: ClientCommand): IO[Unit] =
    command match {
      case ClientCommand.SendMessage(message) => emitBroadcast(user, message)
      case _                                  =>
        logger.warn(s"Unknown command from ${user.name}: $command")
    }

  private def emitBroadcast(sender: User, message: String): IO[Unit] = {
    topic
      .publish1(ChatEvent.Broadcast(sender, message))
      .void *> logger.info(s"User ${sender.name} sent a message")
  }
}
