package chat.client

import fs2.Stream
import chat.model.{ChatEvent, User}
import cats.effect.IO
import cats.syntax.all.*
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

trait EventProcessor {
  def process(event: ChatEvent): IO[Unit]
}

final class ConsoleEventProcessor(clientUsername: String)
    extends EventProcessor {

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

  private def fmtTime(timestamp: Instant): String =
    s"${Color.Gray}[${timeFormatter.format(timestamp)}]${Color.Reset}"

  private def toString(event: ChatEvent): String =
    event match {
      case ChatEvent.UserJoined(_, user, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Green}** ${user.name} joined **${Color.Reset}"
      case ChatEvent.UserLeft(_, user, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Yellow}** ${user.name} left **${Color.Reset}"
      case ChatEvent.Broadcast(_, user, message, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Cyan}${user.name}${Color.Reset}: $message"
      case ChatEvent.DirectMessage(_, sender, recipient, message, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Pink}[${sender.name} whispers]:${Color.Reset} $message"
      case ChatEvent.MessageRejected(_, messageId, timestamp, reason) =>
        s"${fmtTime(timestamp)} ${Color.Red}** $reason **${Color.Reset}"
      case _ => ""
    }

  private def shouldDisplay(event: ChatEvent): Boolean = {
    event match {
      case ChatEvent.UserJoined(_, user, _)       => user.name != clientUsername
      case ChatEvent.UserLeft(_, user, _)         => user.name != clientUsername
      case ChatEvent.Broadcast(_, user, _, _)     => user.name != clientUsername
      case ChatEvent.MessageAccepted(_, _, _)     => false
      case ChatEvent.MessageRejected(_, _, _, _)  => true
      case ChatEvent.DirectMessage(_, _, _, _, _) => true
    }
  }

  override def process(event: ChatEvent): IO[Unit] = {
    IO.whenA(shouldDisplay(event)) {
      IO.println(toString(event))
    }
  }
}
