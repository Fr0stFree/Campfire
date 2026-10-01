package chat.client.console

import cats.effect.IO
import chat.model.ChatEvent

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

final class ConsoleEventProcessor(
    clientUsername: String,
    console: Console
) extends EventProcessor {

  private object Color {
    val Reset = "\u001b[0m"
    val Gray = "\u001b[90m"
    val Red = "\u001b[31m"
    val Green = "\u001b[32m"
    val Yellow = "\u001b[33m"
    val Cyan = "\u001b[36m"
    val Pink = "\u001b[35m"
  }

  private val timeFormatter = DateTimeFormatter
    .ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  override def process(event: ChatEvent): IO[Unit] =
    if shouldDisplay(event) then console.printLine(render(event))
    else IO.unit

  private def render(event: ChatEvent): String =
    event match {
      case ChatEvent.UserJoined(_, user, timestamp) =>
        s"${formatTime(timestamp)} ${Color.Green}** ${user.name} joined **${Color.Reset}"

      case ChatEvent.UserLeft(_, user, timestamp) =>
        s"${formatTime(timestamp)} ${Color.Yellow}** ${user.name} left **${Color.Reset}"

      case ChatEvent.Broadcast(_, user, message, timestamp) =>
        s"${formatTime(timestamp)} ${Color.Cyan}${user.name}${Color.Reset}: $message"

      case ChatEvent.DirectMessage(_, sender, recipient, message, timestamp)
          if recipient.name == clientUsername =>
        s"${formatTime(timestamp)} ${Color.Pink}${sender.name} -> you: ${Color.Reset}$message"

      case ChatEvent.DirectMessage(_, sender, recipient, message, timestamp) =>
        s"${formatTime(timestamp)} ${Color.Pink}you -> ${recipient.name}: ${Color.Reset}$message"

      case ChatEvent.UsersListed(_, users, timestamp) =>
        val names = users.map(_.name).sorted.mkString(", ")
        s"${formatTime(timestamp)} ${Color.Green}Online:${Color.Reset} $names"

      case ChatEvent.MessageRejected(_, timestamp, reason) =>
        s"${formatTime(timestamp)} ${Color.Red}** $reason **${Color.Reset}"

      case ChatEvent.MessageAccepted(_, timestamp) =>
        s"${formatTime(timestamp)} ${Color.Green}Message delivered${Color.Reset}"
    }

  private def shouldDisplay(event: ChatEvent): Boolean =
    event match {
      case ChatEvent.UserJoined(_, user, _) => user.name != clientUsername
      case ChatEvent.UserLeft(_, user, _)   => user.name != clientUsername
      case _                                => true
    }

  private def formatTime(timestamp: Instant): String =
    s"${Color.Gray}[${timeFormatter.format(timestamp)}]${Color.Reset}"
}
