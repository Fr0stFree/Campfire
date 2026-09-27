package chat.client

import chat.model.ClientCommand

enum ConsoleCommand {
  case Send(command: ClientCommand)
  case Quit
}

object ConsoleCommand {
  def fromString(input: String): Option[ConsoleCommand] =
    input.trim match {
      case ""       => None
      case "/quit"  => Some(ConsoleCommand.Quit)
      case "/users" => Some(ConsoleCommand.Send(ClientCommand.ListUsers))
      case message if message.startsWith("/") => None // Unknown command
      case message                            =>
        Some(ConsoleCommand.Send(ClientCommand.SendMessage(message)))
    }
}

