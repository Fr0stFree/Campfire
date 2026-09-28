package chat.service

import cats.data.EitherT
import cats.effect.std.UUIDGen
import cats.effect.{Clock, IO, Ref}
import cats.syntax.all.*
import chat.model.{ChatEvent, ClientCommand, User, UserJoinError, UserSession}
import org.typelevel.log4cats.Logger

import java.util.UUID

private[service] final class ChatServiceImpl(
    sessions: Ref[IO, Map[String, UserSession]]
)(using logger: Logger[IO])
    extends ChatService {

  private val queueSize = 10

  override def join(user: User): EitherT[IO, UserJoinError, UserSession] =
    for {
      _ <- EitherT.fromEither[IO](validateUsername(user.name))
      session <- EitherT.liftF(UserSession.create(user, queueSize))
      _ <- registerSession(session)
      _ <- EitherT.liftF(emitUserJoinedEvent(user))
    } yield session

  override def leave(session: UserSession): IO[Unit] =
    sessions
      .modify { current =>
        val isRegistered = current.get(session.user.name).contains(session)
        val updated =
          if isRegistered then current.removed(session.user.name) else current
        (updated, isRegistered)
      }
      .flatMap(removed =>
        IO.whenA(removed)(emitUserLeftEvent(session.user).void)
      )

  override def handle(
      session: UserSession,
      command: ClientCommand
  ): IO[Unit] =
    command match {
      case ClientCommand.SendBroadcastMessage(message) =>
        emitBroadcastEvent(session.user, message).void
      case ClientCommand.SendDirectMessage(recipient, message) =>
        handleDirectMessage(session, recipient, message)
      case ClientCommand.ListUsers =>
        emitUsersListedEvent(session).void
    }

  private def validateUsername(username: String): Either[UserJoinError, Unit] =
    if username.matches("^[a-zA-Z0-9]{3,20}$") then Right(())
    else Left(UserJoinError.InvalidUsername(username))

  private def registerSession(
      session: UserSession
  ): EitherT[IO, UserJoinError, Unit] =
    EitherT {
      sessions.modify { current =>
        val username = session.user.name
        if current.contains(username) then
          (current, Left(UserJoinError.UsernameTaken(username)))
        else (current.updated(username, session), Right(()))
      }
    }

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
            s"Recipient '$recipient' not found"
          )
      }
    } yield ()

  private def emitDirectMessageEvent(
      id: UUID,
      sender: UserSession,
      recipient: UserSession,
      message: String
  ): IO[ChatEvent.DirectMessage] =
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

  private def emitUsersListedEvent(
      session: UserSession
  ): IO[ChatEvent.UsersListed] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      users <- sessions.get.map(_.values.map(_.user).toList.sortBy(_.name))
      event: ChatEvent.UsersListed = ChatEvent.UsersListed(id, users, time)
      _ <- session.outgoing.offer(event)
    } yield event

  private def emitMessageAcceptedEvent(
      session: UserSession,
      messageId: UUID
  ): IO[ChatEvent.MessageAccepted] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.MessageAccepted =
        ChatEvent.MessageAccepted(id, messageId, time)
      _ <- session.outgoing.offer(event)
    } yield event

  private def emitMessageRejectedEvent(
      session: UserSession,
      messageId: UUID,
      reason: String
  ): IO[ChatEvent.MessageRejected] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.MessageRejected =
        ChatEvent.MessageRejected(id, messageId, time, reason)
      _ <- session.outgoing.offer(event)
      _ <- logger.info(
        s"Message $messageId rejected for user ${session.user.name}: $reason"
      )
    } yield event

  private def emitUserJoinedEvent(user: User): IO[ChatEvent.UserJoined] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.UserJoined = ChatEvent.UserJoined(id, user, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${user.name} joined the chat")
    } yield event

  private def emitUserLeftEvent(user: User): IO[ChatEvent.UserLeft] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.UserLeft = ChatEvent.UserLeft(id, user, time)
      _ <- broadcast(event)
      _ <- logger.info(s"User ${user.name} left the chat")
    } yield event

  private def emitBroadcastEvent(
      sender: User,
      message: String
  ): IO[ChatEvent.Broadcast] =
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

  private def broadcast(event: ChatEvent): IO[Unit] =
    sessions.get.flatMap(_.values.toList.traverse_(_.outgoing.offer(event)))
}
