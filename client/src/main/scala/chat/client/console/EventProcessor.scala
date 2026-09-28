package chat.client.console

import cats.effect.IO
import chat.model.ChatEvent

trait EventProcessor {
  def process(event: ChatEvent): IO[Unit]
}
