package chat.client

enum ClientCommand {
  case SendMessage(message: String)
  case Quit
}

object ClientCommand {
  def fromString(input: String): Option[ClientCommand] =
    input.trim match {
      case ""      => None
      case "/quit" => Some(Quit)
      case message => Some(SendMessage(message))
    }
}
