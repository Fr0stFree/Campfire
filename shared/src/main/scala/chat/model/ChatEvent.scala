package chat.model

import io.circe.{Decoder, Encoder, Json}

enum ChatEvent:
  case UserJoined(user: User)
  case UserLeft(user: User)
  case Message(user: User, text: String)

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
    case ChatEvent.Message(user, text) =>
      Json.obj(
        "type" -> Json.fromString("message"),
        "username" -> Json.fromString(user.name),
        "message" -> Json.fromString(text)
      )
  }

  given Decoder[ChatEvent] = Decoder.instance { cursor =>
    for
      eventType <- cursor.get[String]("type")
      username <- cursor.get[String]("username")
      event <- eventType match
        case "joined" => Right(ChatEvent.UserJoined(User(username)))
        case "left"   => Right(ChatEvent.UserLeft(User(username)))
        case "message" =>
          cursor
            .get[String]("message")
            .map(text => ChatEvent.Message(User(username), text))
        case unknown =>
          Left(
            io.circe.DecodingFailure(
              s"Unknown chat event type: $unknown",
              cursor.history
            )
          )
    yield event
  }
