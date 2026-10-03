package chat.client.console

import chat.model.ClientCommand

enum ConsoleCommand:
  case Send(command: ClientCommand)
  case Quit

object ConsoleCommand:

  def parse(input: String): Option[ConsoleCommand] = input.trim match
    case ""                                 => None
    case "/quit"                            => Some(ConsoleCommand.Quit)
    case "/users"                           => Some(ConsoleCommand.Send(ClientCommand.ListUsers))
    case input if input.startsWith("/msg ") => parseDirectMessage(input)
    case input if input.startsWith("/")     => None
    case message => Some(ConsoleCommand.Send(ClientCommand.SendBroadcastMessage(message)))

  private def parseDirectMessage(input: String): Option[ConsoleCommand] =
    input.split("\\s+", 3) match
      case Array("/msg", recipient, message) if message.trim.nonEmpty =>
        Some(ConsoleCommand.Send(ClientCommand.SendDirectMessage(recipient, message.trim)))
      case _ => None
