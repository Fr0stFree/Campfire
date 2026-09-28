package chat.service
import cats.effect.IO
import chat.model.{ChatEvent, User, UserJoinError, ClientCommand, UserSession}
import java.util.UUID
import fs2.Stream
import fs2.concurrent.Topic
import cats.effect.Ref
import org.typelevel.log4cats.Logger
import cats.effect.Clock
import cats.effect.std.Queue
import cats.syntax.all.*
import cats.data.EitherT
import cats.effect.std.UUIDGen

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
        handleBroadcastMessage(session, message)
      case ClientCommand.SendDirectMessage(recipient, message) =>
        handleDirectMessage(session, recipient, message)
      case _ =>
        logger.warn(s"Unknown command from ${session.user.name}: $command")
    }
  }

  private def handleBroadcastMessage(
      session: UserSession,
      message: String
  ): IO[Unit] = emitBroadcastEvent(session.user, message).void

  private def handleDirectMessage(
      sender: UserSession,
      recipient: String,
      message: String
  ): IO[Unit] =
    for {
      messageId <- UUIDGen.randomUUID[IO]
      currentSessions <- sessions.get
      _ <- currentSessions.get(recipient) match {
        case Some(recipientSession) =>
          emitDirectMessageEvent(
            messageId,
            sender,
            recipientSession,
            message
          ) *> emitMessageAcceptedEvent(sender, messageId)

        case None =>
          emitMessageRejectedEvent(
            sender,
            messageId,
            reason = s"Recipient '$recipient' not found"
          )
      }
    } yield ()

  private def emitDirectMessageEvent(
      id: UUID,
      sender: UserSession,
      recipient: UserSession,
      message: String
  ): IO[ChatEvent.DirectMessage] = {
    for {
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.DirectMessage = ChatEvent.DirectMessage(
        id,
        sender.user,
        recipient.user,
        message,
        time
      )
      _ <- recipient.outgoing.offer(event)
      _ <- logger.info(
        s"User ${sender.user.name} sent a direct message to ${recipient.user.name}"
      )
    } yield event
  }

  private def emitMessageAcceptedEvent(
      session: UserSession,
      messageId: UUID
  ): IO[ChatEvent.MessageAccepted] = {
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.MessageAccepted = ChatEvent.MessageAccepted(
        id,
        messageId,
        time
      )
      _ <- session.outgoing.offer(event)
      _ <- logger.info(
        s"Message ${messageId} accepted for user ${session.user.name}"
      )
    } yield event
  }

  private def emitMessageRejectedEvent(
      session: UserSession,
      messageId: UUID,
      reason: String
  ): IO[ChatEvent.MessageRejected] = {
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.MessageRejected = ChatEvent.MessageRejected(
        id,
        messageId,
        time,
        reason
      )
      _ <- session.outgoing.offer(event)
      _ <- logger.info(
        s"Message ${messageId} rejected for user ${session.user.name}: $reason"
      )
    } yield event
  }

  private def emitUserJoinedEvent(user: User): IO[ChatEvent.UserJoined] = {
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.UserJoined = ChatEvent.UserJoined(id, user, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${user.name} joined the chat")
    } yield event
  }

  private def emitUserLeftEvent(user: User): IO[ChatEvent.UserLeft] = {
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.UserLeft = ChatEvent.UserLeft(id, user, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${user.name} left the chat")
    } yield event
  }

  private def emitBroadcastEvent(
      sender: User,
      message: String
  ): IO[ChatEvent.Broadcast] = {
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.Broadcast = ChatEvent.Broadcast(
        id,
        sender,
        message,
        time
      )
      _ <- broadcast(event)
      _ <- logger.info(s"User ${sender.name} sent a message")
    } yield event
  }

  private def broadcast(event: ChatEvent): IO[Unit] =
    sessions.get.flatMap { current =>
      current.values.toList
        .traverse_(_.outgoing.offer(event))
    }

}
