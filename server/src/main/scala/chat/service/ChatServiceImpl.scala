package chat.service

import cats.data.EitherT
import cats.effect.std.UUIDGen
import cats.effect.{Clock, IO}
import cats.syntax.all.*
import chat.model.{ChatEvent, ClientCommand, User}
import chat.storage.{Storage, StorageError}
import org.typelevel.log4cats.Logger

private[service] final class ChatServiceImpl(
    sessions: Storage.UserSessions,
    events: Storage.ChatEvents
)(using
    logger: Logger[IO]
) extends ChatService {

  private val eventFactory = ChatEventFactory()
  private val queueSize = 10
  private val usernameRegex = "^[a-zA-Z0-9]{3,20}$".r

  override def join(user: User): EitherT[IO, UserJoinError, UserSession] =
    for {
      _ <- EitherT.fromEither[IO](validateUsername(user.name))
      session <- EitherT.liftF(UserSession.create(user, queueSize))
      _ <- sessions
        .create(session)
        .leftMap(error => UserJoinError.UsernameTaken(error.username))
      _ <- EitherT.liftF {
        for {
          event <- eventFactory.userJoined(user)
          _ <- events.save(event)
          _ <- sendAll(event)
        } yield ()
      }
    } yield session

  override def leave(session: UserSession): IO[Unit] =
    sessions
      .delete(session)
      .foldF(
        _ => IO.unit,
        _ =>
          for {
            event <- eventFactory.userLeft(session.user)
            _ <- events.save(event)
            _ <- sendAll(event)
          } yield ()
      )

  override def handle(
      session: UserSession,
      command: ClientCommand
  ): IO[Unit] =
    command match {
      case ClientCommand.SendBroadcastMessage(message) =>
        handleBroadcastCommand(session, message)
      case ClientCommand.SendDirectMessage(recipient, message) =>
        handleDirectMessageCommand(session, recipient, message)
      case ClientCommand.ListUsers =>
        handleUsersListedCommand(session)
    }

  private def validateUsername(username: String): Either[UserJoinError, Unit] =
    if usernameRegex.matches(username) then Right(())
    else Left(UserJoinError.InvalidUsername(username))

  private def handleDirectMessageCommand(
      sender: UserSession,
      recipientName: String,
      message: String
  ): IO[Unit] =
    sessions.get(recipientName).value.flatMap {
      case Right(recipient) => {
        for {
          event <- eventFactory.directMessage(sender, recipient, message)
          _ <- IO.both(sendTo(event, recipient), sendTo(event, sender))
          _ <- events.save(event)
          _ <- logger.info(
            s"User ${sender.user.name} sent a direct message to ${recipient.user.name}"
          )
        } yield ()
      }
      case Left(_) => {
        for {
          reason = s"User '$recipientName' not found"
          event <- eventFactory.messageRejected(sender, reason)
          _ <- sendTo(event, sender)
        } yield ()
      }
    }

  private def handleBroadcastCommand(
      sender: UserSession,
      message: String
  ): IO[Unit] =
    for {
      event <- eventFactory.broadcast(sender.user, message)
      _ <- events.save(event)
      _ <- sendAll(event)
    } yield ()

  private def handleUsersListedCommand(destination: UserSession): IO[Unit] =
    for {
      users <- sessions.list.map(_.map(_.user).sortBy(_.name))
      event <- eventFactory.usersListed(destination, users)
      _ <- sendTo(event, destination)
    } yield ()

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
