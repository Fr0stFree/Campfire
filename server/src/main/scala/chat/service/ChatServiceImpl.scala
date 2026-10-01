package chat.service

import cats.data.EitherT
import cats.effect.std.UUIDGen
import cats.effect.{Clock, IO}
import cats.syntax.all.*
import chat.model.{ChatEvent, ClientCommand, User}
import chat.storage.{Storage, StorageError}
import org.typelevel.log4cats.Logger

import java.util.UUID

private[service] final class ChatServiceImpl(
    sessions: Storage.UserSessions
)(using logger: Logger[IO])
    extends ChatService {

  private val queueSize = 10

  override def join(user: User): EitherT[IO, UserJoinError, UserSession] =
    val toUserNameTakenError = (error: StorageError.UserSessionAlreadyExists) =>
      UserJoinError.UsernameTaken(error.username)

    for {
      _ <- EitherT.fromEither[IO](validateUsername(user.name))
      session <- EitherT.liftF(UserSession.create(user, queueSize))
      _ <- sessions.create(session).leftMap(toUserNameTakenError)
      _ <- EitherT.liftF(emitUserJoinedEvent(user))
    } yield session

  override def leave(session: UserSession): IO[Unit] =
    sessions
      .delete(session)
      .foldF(
        _ => IO.unit,
        _ => emitUserLeftEvent(session.user).void
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

  private def handleDirectMessage(
      sender: UserSession,
      recipientName: String,
      message: String
  ): IO[Unit] =
    for {
      messageId <- UUIDGen.randomUUID[IO]
      _ <- sessions.get(recipientName).value.flatMap {
        case Right(recipient) =>
          emitDirectMessageEvent(
            sender,
            recipient,
            destination = recipient,
            message
          ) *> emitDirectMessageEvent(
            sender,
            recipient,
            destination = sender,
            message
          )
        case Left(_) =>
          emitMessageRejectedEvent(
            sender,
            messageId, // TODO: Consider generating a unique ID for the rejected message
            s"Recipient '$recipientName' not found"
          )
      }
    } yield ()

  private def emitDirectMessageEvent(
      sender: UserSession,
      recipient: UserSession,
      destination: UserSession,
      message: String
  ): IO[ChatEvent.DirectMessage] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.DirectMessage = ChatEvent.DirectMessage(
        id,
        sender.user,
        recipient.user,
        message,
        time
      )
      _ <- sendTo(event, destination)
      _ <- logger.info(
        s"User ${sender.user.name} sent a direct message to ${recipient.user.name}"
      )
    } yield event

  private def emitUsersListedEvent(
      destination: UserSession
  ): IO[ChatEvent.UsersListed] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      users <- sessions.list.map(_.map(_.user).sortBy(_.name))
      event: ChatEvent.UsersListed = ChatEvent.UsersListed(id, users, time)
      _ <- sendTo(event, destination)
    } yield event

  private def emitMessageAcceptedEvent(
      destination: UserSession,
      messageId: UUID
  ): IO[ChatEvent.MessageAccepted] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.MessageAccepted =
        ChatEvent.MessageAccepted(id, messageId, time)
      _ <- sendTo(event, destination)
    } yield event

  private def emitMessageRejectedEvent(
      destination: UserSession,
      messageId: UUID,
      reason: String
  ): IO[ChatEvent.MessageRejected] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.MessageRejected =
        ChatEvent.MessageRejected(id, messageId, time, reason)
      _ <- sendTo(event, destination)
      _ <- logger.info(
        s"Message $messageId rejected for user ${destination.user.name}: $reason"
      )
    } yield event

  private def emitUserJoinedEvent(user: User): IO[ChatEvent.UserJoined] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.UserJoined = ChatEvent.UserJoined(id, user, time)
      _ <- sendAll(event)
      _ <- logger.info(s"User ${user.name} joined the chat")
    } yield event

  private def emitUserLeftEvent(user: User): IO[ChatEvent.UserLeft] =
    for {
      id <- UUIDGen.randomUUID[IO]
      time <- Clock[IO].realTimeInstant
      event: ChatEvent.UserLeft = ChatEvent.UserLeft(id, user, time)
      _ <- sendAll(event)
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
      _ <- sendAll(event)
      _ <- logger.info(s"User ${sender.name} sent a message")
    } yield event

  private def sendAll(event: ChatEvent): IO[Unit] = {
    sessions.list.flatMap { allSessions =>
      allSessions.toSeq.traverse_ { session =>
        sendTo(event, session)
      }
    }
  }

  private def sendTo(event: ChatEvent, destination: UserSession): IO[Unit] = {
    destination.outgoing.offer(event)
  }
}
