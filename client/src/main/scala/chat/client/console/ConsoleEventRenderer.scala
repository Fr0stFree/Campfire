package chat.client.console

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import chat.model.ChatEvent

final class ConsoleEventRenderer(clientUsername: String):

  private object Color:
    val Reset = "\u001b[0m"
    val Gray = "\u001b[90m"
    val Red = "\u001b[31m"
    val Green = "\u001b[32m"
    val Yellow = "\u001b[33m"
    val Cyan = "\u001b[36m"
    val Pink = "\u001b[35m"

  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  def render(event: ChatEvent): Option[String] = event match
    case ChatEvent.UserJoined(_, user, timestamp) if user.name != clientUsername =>
      Some(s"${formatTime(timestamp)} ${Color.Green}** ${user.name} joined **${Color.Reset}")

    case ChatEvent.UserLeft(_, user, timestamp) if user.name != clientUsername =>
      Some(s"${formatTime(timestamp)} ${Color.Yellow}** ${user.name} left **${Color.Reset}")

    case ChatEvent.UserJoined(_, _, _) | ChatEvent.UserLeft(_, _, _) => None

    case ChatEvent.Broadcast(_, user, message, timestamp) =>
      Some(s"${formatTime(timestamp)} ${Color.Cyan}${user.name}${Color.Reset}: $message")

    case ChatEvent.DirectMessage(_, sender, recipient, message, timestamp)
      if recipient.name == clientUsername =>
      Some(s"${formatTime(timestamp)} ${Color.Pink}${sender.name} -> you: ${Color.Reset}$message")

    case ChatEvent.DirectMessage(_, sender, recipient, message, timestamp) =>
      Some(s"${formatTime(timestamp)} ${Color.Pink}you -> ${recipient.name}: ${Color.Reset}$message")

    case ChatEvent.UsersListed(_, users, timestamp) =>
      val names = users.map(_.name).sorted.mkString(", ")
      Some(s"${formatTime(timestamp)} ${Color.Green}Online:${Color.Reset} $names")

    case ChatEvent.MessageRejected(_, timestamp, reason) =>
      Some(s"${formatTime(timestamp)} ${Color.Red}** $reason **${Color.Reset}")

    case ChatEvent.MessageAccepted(_, timestamp) =>
      Some(s"${formatTime(timestamp)} ${Color.Green}Message delivered${Color.Reset}")

  private def formatTime(timestamp: Instant): String =
    s"${Color.Gray}[${timeFormatter.format(timestamp)}]${Color.Reset}"
