package chat.client.console

import cats.effect.{IO, Resource}
import org.jline.reader.{EndOfFileException, LineReader, LineReaderBuilder}
import org.jline.terminal.{Terminal, TerminalBuilder}

final class Console private (reader: LineReader) {

  def readLine: IO[Option[String]] = {
    IO.blocking {
      val line = reader.readLine("> ")
      reader.getTerminal.writer().print("\u001b[1A\u001b[2K\r")
      reader.getTerminal.writer().flush()
      Option(line)
    }.handleError { case _: EndOfFileException => None }
  }

  def printLine(message: String): IO[Unit] = {
    IO.blocking(reader.printAbove(message))
  }
}

object Console {

  def resource: Resource[IO, Console] =
    Resource
      .fromAutoCloseable(
        IO.blocking(TerminalBuilder.builder().system(true).build())
      )
      .map { terminal =>
        new Console(
          LineReaderBuilder.builder().terminal(terminal).build()
        )
      }
}
