package chat.service
import cats.effect.IO
import chat.model.{ChatEvent, User, UserJoinError, ClientCommand, UserSession}
import fs2.Stream
import fs2.concurrent.Topic
import cats.effect.Ref
import org.typelevel.log4cats.Logger
import cats.effect.Clock
import cats.effect.std.Queue
import cats.syntax.all.*
import cats.data.EitherT

trait ChatService {
  def join(user: User): EitherT[IO, UserJoinError, UserSession]
  def leave(session: UserSession): IO[Unit]
  def handle(session: UserSession, command: ClientCommand): IO[Unit]
}

object ChatService {
  def build(using Logger[IO]): IO[ChatService] = {
    for {
      sessions <- Ref.of[IO, Map[String, UserSession]](Map.empty)
    } yield ChatServiceImpl(sessions)
  }
}

final class ChatServiceImpl(sessions: Ref[IO, Map[String, UserSession]])(using
    logger: Logger[IO]
) extends ChatService {

  private val queueSize: Int = 10

  override def join(user: User): EitherT[IO, UserJoinError, UserSession] = {
    for {
      _ <- EitherT.fromEither[IO](validateUsername(user.name))
      session <- EitherT.liftF(UserSession.create(user, queueSize))
      _ <- registerSession(user, session)
      _ <- EitherT.liftF(emitUserJoinedEvent(user))
    } yield session
  }

  private def validateUsername(
      username: String
  ): Either[UserJoinError, Unit] = {
    val usernamePattern = "^[a-zA-Z0-9]{3,20}$".r
    username match {
      case usernamePattern() => Right(())
      case _                 => Left(UserJoinError.InvalidUsername(username))
    }
  }

  private def registerSession(
      user: User,
      session: UserSession
  ): EitherT[IO, UserJoinError, Unit] = {
    EitherT {
      sessions.modify { current =>
        if (current.contains(user.name)) {
          (current, Left(UserJoinError.UsernameTaken(user.name)))
        } else {
          (current + (user.name -> session), Right(()))
        }
      }
    }
  }

  override def leave(session: UserSession): IO[Unit] = {
    for {
      _ <- sessions.update(_.removed(session.user.name))
      _ <- emitUserLeftEvent(session.user)
    } yield ()
  }

  override def handle(
      session: UserSession,
      command: ClientCommand
  ): IO[Unit] = {
    command match {
      case ClientCommand.SendBroadcastMessage(message) =>
        emitBroadcastEvent(session.user, message)
      case _ =>
        logger.warn(s"Unknown command from ${session.user.name}: $command")
    }
  }

  private def emitUserJoinedEvent(user: User): IO[Unit] = {
    for {
      time <- Clock[IO].realTimeInstant
      event = ChatEvent.UserJoined(user, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${user.name} joined the chat")
    } yield ()
  }

  private def emitUserLeftEvent(user: User): IO[Unit] = {
    for {
      time <- Clock[IO].realTimeInstant
      event = ChatEvent.UserLeft(user, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${user.name} left the chat")
    } yield ()
  }

  private def emitBroadcastEvent(sender: User, message: String): IO[Unit] = {
    for {
      time <- Clock[IO].realTimeInstant
      event = ChatEvent.Broadcast(sender, message, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${sender.name} sent a message")
    } yield ()
  }

  private def broadcast(event: ChatEvent): IO[Unit] =
    sessions.get.flatMap { current =>
      current.values.toList
        .traverse_(_.outgoing.offer(event))
    }

}
