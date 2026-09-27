package chat.client
import fs2.Stream
import chat.model.{ChatEvent, User}
import cats.effect.IO
import cats.syntax.all.*

trait EventProcessor {
  def process(event: ChatEvent): IO[Unit]
}

final class ConsoleEventProcessor(username: String) extends EventProcessor {
  private val renderer = ConsoleRenderer(username)

  override def process(event: ChatEvent): IO[Unit] = {
    renderer
      .render(event)
      .traverse_(IO.println)
  }
}

final class ConsoleRenderer(username: String) {
  def render(event: ChatEvent): Option[String] =
    event match {
      case ChatEvent.UserJoined(user) => Some(s"* ${user.name} joined")
      case ChatEvent.UserLeft(user)   => Some(s"* ${user.name} left")
      case ChatEvent.Broadcast(user, _) if username == user.name => None
      case ChatEvent.Broadcast(user, text) => Some(s"${user.name}: $text")
    }
}
