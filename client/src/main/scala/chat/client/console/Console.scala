package chat.client.console

import cats.effect.{IO, Resource}
import fs2.Stream
import org.jline.reader.{EndOfFileException, LineReader, LineReaderBuilder}
import org.jline.terminal.TerminalBuilder

final class Console private (reader: LineReader):

  def readLine: IO[Option[String]] = IO.blocking {
    val line = reader.readLine("> ")
    reader.getTerminal.writer().print("\u001b[1A\u001b[2K\r")
    reader.getTerminal.writer().flush()
    Option(line)
  }.handleErrorWith {
    case _: EndOfFileException => IO.pure(None)
    case error                 => IO.raiseError(error)
  }

  def printLine(message: String): IO[Unit] = IO.blocking(reader.printAbove(message))

  def commands: Stream[IO, ConsoleCommand] = Stream.repeatEval(readLine).unNoneTerminate
    .map(ConsoleCommand.parse).unNone

object Console:

  def resource: Resource[IO, Console] = Resource
    .fromAutoCloseable(IO.blocking(TerminalBuilder.builder().system(true).build()))
    .map(terminal => new Console(LineReaderBuilder.builder().terminal(terminal).build()))
