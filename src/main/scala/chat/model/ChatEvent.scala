package chat.model

import io.circe.Codec
import io.circe.{Encoder, Json}

enum ChatEvent {
  case UserJoined(user: User)
  case UserLeft(user: User)
  case Message(user: User, text: String)
}

object ChatEvent:
  given Encoder[ChatEvent] = Encoder.instance {
    case ChatEvent.UserJoined(user) =>
      Json.obj(
        "type" -> Json.fromString("joined"),
        "username" -> Json.fromString(user.name)
      )

    case ChatEvent.UserLeft(user) =>
      Json.obj(
        "type" -> Json.fromString("left"),
        "username" -> Json.fromString(user.name)
      )

    case ChatEvent.Message(user, message) =>
      Json.obj(
        "type" -> Json.fromString("message"),
        "username" -> Json.fromString(user.name),
        "message" -> Json.fromString(message)
      )

  }
