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
  }

  private val timeFormatter = DateTimeFormatter
    .ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  private def fmtTime(timestamp: Instant): String =
    s"${Color.Gray}[${timeFormatter.format(timestamp)}]${Color.Reset}"

  private def toString(event: ChatEvent): String =
    event match {
      case ChatEvent.UserJoined(user, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Green}** ${user.name} joined **${Color.Reset}"
      case ChatEvent.UserLeft(user, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Yellow}** ${user.name} left **${Color.Reset}"
      case ChatEvent.Broadcast(user, message, timestamp) =>
        s"${fmtTime(timestamp)} ${Color.Cyan}${user.name}${Color.Reset}: $message"
    }

  private def shouldDisplay(event: ChatEvent): Boolean = {
    event match {
      case ChatEvent.UserJoined(user, _)   => user.name != clientUsername
      case ChatEvent.UserLeft(user, _)     => user.name != clientUsername
      case ChatEvent.Broadcast(user, _, _) => user.name != clientUsername
    }
  }

  override def process(event: ChatEvent): IO[Unit] = {
    IO.whenA(shouldDisplay(event)) {
      IO.println(toString(event))
    }
  }
}
