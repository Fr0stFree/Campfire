package chat.model

import io.circe.{Decoder, Encoder, Json}

enum ClientCommand {
  case SendBroadcastMessage(text: String)
  case SendDirectMessage(recipient: String, text: String)
  case ListUsers
}

object ClientCommand {
  given Encoder[ClientCommand] = Encoder.instance {
    case ClientCommand.SendBroadcastMessage(text) =>
      Json.obj(
        "type" -> Json.fromString("broadcast_message"),
        "text" -> Json.fromString(text)
      )

    case ClientCommand.ListUsers =>
      Json.obj(
        "type" -> Json.fromString("list_users")
      )
    case ClientCommand.SendDirectMessage(recipient, text) =>
      Json.obj(
        "type" -> Json.fromString("direct_message"),
        "recipient" -> Json.fromString(recipient),
        "text" -> Json.fromString(text)
      )
  }

  given Decoder[ClientCommand] =
    Decoder.instance { cursor =>
      cursor.get[String]("type").flatMap {
        case "broadcast_message" =>
          cursor
            .get[String]("text")
            .map(ClientCommand.SendBroadcastMessage.apply)

        case "list_users" =>
          Right(ClientCommand.ListUsers)

        case "direct_message" =>
          for {
            recipient <- cursor.get[String]("recipient")
            text <- cursor.get[String]("text")
          } yield ClientCommand.SendDirectMessage(recipient, text)

        case other =>
          Left(
            io.circe.DecodingFailure(
              s"Unknown command type: $other",
              cursor.history
            )
          )
      }
    }
}
