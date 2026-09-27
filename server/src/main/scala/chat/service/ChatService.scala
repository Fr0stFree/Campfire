package chat.service
import cats.effect.IO
import chat.model.{ChatEvent, User, UserConnectResult, ClientCommand}
import fs2.Stream
import fs2.concurrent.Topic
import cats.effect.Ref
import org.typelevel.log4cats.Logger
import cats.effect.Clock

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
          Clock[IO].realTimeInstant.flatMap { timestamp =>
            topic
              .publish1(ChatEvent.UserJoined(user, timestamp))
              .void *>
              logger.info(s"User ${user.name} joined the chat")
          }
        case UserConnectResult.UsernameTaken => IO.unit
      }
  }

  override def leave(user: User): IO[Unit] = {
    for {
      _ <- users.update(_.excl(user))
      time <- Clock[IO].realTimeInstant
      _ <- topic.publish1(ChatEvent.UserLeft(user, time))
      _ <- logger.info(s"User ${user.name} left the chat")
    } yield ()
  }

  override def handle(user: User, command: ClientCommand): IO[Unit] =
    command match {
      case ClientCommand.SendMessage(message) => emitBroadcast(user, message)
      case _                                  =>
        logger.warn(s"Unknown command from ${user.name}: $command")
    }

  private def emitBroadcast(sender: User, message: String): IO[Unit] = {
    for {
      time <- Clock[IO].realTimeInstant
      _ <- topic.publish1(ChatEvent.Broadcast(sender, message, time))
      _ <- logger.info(s"User ${sender.name} sent a message")
    } yield ()
  }
}
