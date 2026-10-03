package chat.service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.Logger

import chat.model.{ChatEvent, ClientCommand, User}
import chat.storage.{ChatEventFilter, Storage}

final private[service] class ChatServiceImpl(
  sessions: Storage.UserSessions,
  events: Storage.ChatEvents
)(using logger: Logger[IO])
    extends ChatService:

  private val eventFactory = new ChatEventFactory[IO]
  private val queueSize = 10
  private val usernameRegex = "^[a-zA-Z0-9]{3,20}$".r
  private val historyLimit = 10

  override def join(user: User): EitherT[IO, UserJoinError, UserSession] = for {
    _ <- EitherT.fromEither[IO](validateUsername(user.name))
    session <- EitherT.liftF(UserSession.create(user, queueSize))
    _ <- sessions.create(session).leftMap(error => UserJoinError.UsernameTaken(error.username))
    _ <- EitherT.liftF {
      for {
        _ <- logger.info(s"User ${user.name} joined the chat")
        event <- eventFactory.userJoined(user)
        _ <- sendAll(event)
        _ <- events.save(event)
        historyEvents <- events.list(ChatEventFilter(user, historyLimit))
        _ <- historyEvents.traverse_(session.send)
      } yield ()
    }
  } yield session

  override def leave(session: UserSession): IO[Unit] = sessions.delete(session).foldF(
    _ => IO.unit,
    _ =>
      for {
        event <- eventFactory.userLeft(session.user)
        _ <- sendAll(event)
        _ <- events.save(event)
      } yield ()
  )

  override def handle(session: UserSession, command: ClientCommand): IO[Unit] = command match
    case ClientCommand.SendBroadcastMessage(message) => handleBroadcastCommand(session, message)
    case ClientCommand.SendDirectMessage(recipient, message) =>
      handleDirectMessageCommand(session, recipient, message)
    case ClientCommand.ListUsers => handleUsersListedCommand(session)

  private def validateUsername(username: String): Either[UserJoinError, Unit] =
    if usernameRegex.matches(username) then Right(())
    else Left(UserJoinError.InvalidUsername(username))

  private def handleDirectMessageCommand(
    sender: UserSession,
    recipientName: String,
    message: String
  ): IO[Unit] = sessions.get(recipientName).value.flatMap {
    case Right(recipient) => for {
        event <- eventFactory.directMessage(sender.user, recipient.user, message)
        _ <- IO.both(recipient.send(event), sender.send(event))
        _ <- events.save(event)
        _ <- logger
          .info(s"User ${sender.user.name} sent a direct message to ${recipient.user.name}")
      } yield ()
    case Left(_) => for {
        reason = s"User '$recipientName' not found"
        event <- eventFactory.messageRejected(reason)
        _ <- sender.send(event)
      } yield ()
  }

  private def handleBroadcastCommand(sender: UserSession, message: String): IO[Unit] = for {
    event <- eventFactory.broadcast(sender.user, message)
    _ <- sendAll(event)
    _ <- events.save(event)
  } yield ()

  private def handleUsersListedCommand(destination: UserSession): IO[Unit] = for {
    users <- sessions.list.map(_.map(_.user).sortBy(_.name))
    event <- eventFactory.usersListed(users)
    _ <- destination.send(event)
  } yield ()

  private def sendAll(event: ChatEvent): IO[Unit] = sessions.list
    .flatMap(_.traverse_(_.send(event)))
