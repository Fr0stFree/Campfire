package chat.model

import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import java.time.Instant

enum ChatEvent {
  case UserJoined(user: User, timestamp: Instant)
  case UserLeft(user: User, timestamp: Instant)
  case Broadcast(sender: User, message: String, timestamp: Instant)
}

object ChatEvent {
  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] =
    Decoder.decodeString.emapTry(value => scala.util.Try(Instant.parse(value)))

  given Encoder[ChatEvent] = Encoder.instance {
    case ChatEvent.UserJoined(user, timestamp) =>
      Json.obj(
        "type" -> Json.fromString("joined"),
        "username" -> Json.fromString(user.name),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.UserLeft(user, timestamp) =>
      Json.obj(
        "type" -> Json.fromString("left"),
        "username" -> Json.fromString(user.name),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.Broadcast(user, message, timestamp) =>
      Json.obj(
        "type" -> Json.fromString("broadcast"),
        "username" -> Json.fromString(user.name),
        "message" -> Json.fromString(message),
        "timestamp" -> timestamp.asJson
      )
  }

  given Decoder[ChatEvent] = Decoder.instance { cursor =>
    for {
      eventType <- cursor.get[String]("type")
      username <- cursor.get[String]("username")
      timestamp <- cursor.get[Instant]("timestamp")
      event <- eventType match
        case "joined" => Right(ChatEvent.UserJoined(User(username), timestamp))
        case "left"   => Right(ChatEvent.UserLeft(User(username), timestamp))
        case "broadcast" =>
          cursor
            .get[String]("message")
            .map(text => ChatEvent.Broadcast(User(username), text, timestamp))
        case unknown =>
          Left(
            io.circe.DecodingFailure(
              s"Unknown chat event type: $unknown",
              cursor.history
            )
          )
    } yield event
  }
}
