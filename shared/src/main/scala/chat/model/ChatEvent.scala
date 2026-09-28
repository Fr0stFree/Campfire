package chat.model

import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import java.time.Instant
import java.util.UUID

enum ChatEvent {
  case UserJoined(id: UUID, user: User, timestamp: Instant)
  case UserLeft(id: UUID, user: User, timestamp: Instant)
  case Broadcast(id: UUID, sender: User, message: String, timestamp: Instant)
  case DirectMessage(
      id: UUID,
      sender: User,
      recipient: User,
      message: String,
      timestamp: Instant
  )
  case MessageAccepted(id: UUID, messageId: UUID, timestamp: Instant)
  case MessageRejected(
      id: UUID,
      messageId: UUID,
      timestamp: Instant,
      reason: String
  )
}

object ChatEvent {
  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] =
    Decoder.decodeString.emapTry(value => scala.util.Try(Instant.parse(value)))

  given Encoder[ChatEvent] = Encoder.instance {
    case ChatEvent.UserJoined(id, user, timestamp) =>
      Json.obj(
        "id" -> Json.fromString(id.toString()),
        "type" -> Json.fromString("joined"),
        "username" -> Json.fromString(user.name),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.UserLeft(id, user, timestamp) =>
      Json.obj(
        "id" -> Json.fromString(id.toString()),
        "type" -> Json.fromString("left"),
        "username" -> Json.fromString(user.name),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.Broadcast(id, user, message, timestamp) =>
      Json.obj(
        "id" -> Json.fromString(id.toString()),
        "type" -> Json.fromString("broadcast"),
        "username" -> Json.fromString(user.name),
        "message" -> Json.fromString(message),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.DirectMessage(id, sender, recipient, message, timestamp) =>
      Json.obj(
        "id" -> Json.fromString(id.toString()),
        "type" -> Json.fromString("direct_message"),
        "sender" -> Json.fromString(sender.name),
        "recipient" -> Json.fromString(recipient.name),
        "message" -> Json.fromString(message),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.MessageAccepted(id, messageId, timestamp) =>
      Json.obj(
        "id" -> Json.fromString(id.toString()),
        "type" -> Json.fromString("message_accepted"),
        "message_id" -> Json.fromString(messageId.toString()),
        "timestamp" -> timestamp.asJson
      )
    case ChatEvent.MessageRejected(id, messageId, timestamp, reason) =>
      Json.obj(
        "id" -> Json.fromString(id.toString()),
        "type" -> Json.fromString("message_rejected"),
        "message_id" -> Json.fromString(messageId.toString()),
        "reason" -> Json.fromString(reason),
        "timestamp" -> timestamp.asJson
      )
  }

  given Decoder[ChatEvent] = Decoder.instance { cursor =>
    for {
      id <- cursor.get[UUID]("id")
      eventType <- cursor.get[String]("type")
      timestamp <- cursor.get[Instant]("timestamp")

      event <- eventType match {
        case "joined" =>
          cursor
            .get[String]("username")
            .map(username =>
              ChatEvent.UserJoined(
                id,
                User(username),
                timestamp
              )
            )
        case "left" =>
          cursor
            .get[String]("username")
            .map(username =>
              ChatEvent.UserLeft(
                id,
                User(username),
                timestamp
              )
            )
        case "broadcast" =>
          for {
            username <- cursor.get[String]("username")
            message <- cursor.get[String]("message")
          } yield ChatEvent.Broadcast(
            id,
            User(username),
            message,
            timestamp
          )

        case "direct_message" =>
          for {
            sender <- cursor.get[String]("sender")
            recipient <- cursor.get[String]("recipient")
            message <- cursor.get[String]("message")
          } yield ChatEvent.DirectMessage(
            id,
            User(sender),
            User(recipient),
            message,
            timestamp
          )

        case "message_accepted" =>
          cursor
            .get[UUID]("message_id")
            .map(messageId =>
              ChatEvent.MessageAccepted(
                id,
                messageId,
                timestamp
              )
            )
        case "message_rejected" =>
          for {
            messageId <- cursor.get[UUID]("message_id")
            reason <- cursor.get[String]("reason")
          } yield ChatEvent.MessageRejected(
            id,
            messageId,
            timestamp,
            reason
          )
        case unknown =>
          Left(
            io.circe.DecodingFailure(
              s"Unknown chat event type: $unknown",
              cursor.history
            )
          )
      }
    } yield event
  }
}
