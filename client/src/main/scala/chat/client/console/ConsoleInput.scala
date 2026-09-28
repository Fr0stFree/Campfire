package chat.client.console

import cats.effect.IO
import fs2.Stream

object ConsoleInput {

  def commands(console: Console): Stream[IO, ConsoleCommand] =
    Stream
      .repeatEval(console.readLine)
      .unNoneTerminate
      .map(ConsoleCommand.parse)
      .unNone
}
