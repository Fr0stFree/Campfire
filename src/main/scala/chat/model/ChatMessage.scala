package chat.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class ChatMessage(
    username: String,
    message: String
)

object ChatMessage {
  given Codec[ChatMessage] = deriveCodec
}
