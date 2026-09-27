package chat.model

import io.circe.{Decoder, Encoder, Json}

enum ClientCommand {
  case SendMessage(text: String)
  case ListUsers
}

object ClientCommand {
  given Encoder[ClientCommand] = Encoder.instance {
    case ClientCommand.SendMessage(text) =>
      Json.obj(
        "type" -> Json.fromString("message"),
        "text" -> Json.fromString(text)
      )

    case ClientCommand.ListUsers =>
      Json.obj(
        "type" -> Json.fromString("list_users")
      )
  }

  given Decoder[ClientCommand] =
    Decoder.instance { cursor =>
      cursor.get[String]("type").flatMap {
        case "message" =>
          cursor
            .get[String]("text")
            .map(ClientCommand.SendMessage.apply)

        case "list_users" =>
          Right(ClientCommand.ListUsers)

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
