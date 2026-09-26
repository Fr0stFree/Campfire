package chat.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class User(name: String)

object User:
  given Codec.AsObject[User] = deriveCodec
