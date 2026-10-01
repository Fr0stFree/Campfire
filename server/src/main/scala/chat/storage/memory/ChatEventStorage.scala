package chat.storage.memory

import cats.effect.{IO, Ref}
import chat.storage.{Storage, StorageError}
import chat.model.ChatEvent

final class ChatEventStorage(
    events: Ref[IO, Seq[ChatEvent]]
) extends Storage.ChatEvents {
  override def save(event: ChatEvent): IO[Unit] = {
    events.update(_.appended(event))
  }

  override def list: IO[Seq[ChatEvent]] = {
    events.get
  }
}

object ChatEventStorage {
  def build: IO[ChatEventStorage] = {
    for {
      ref <- Ref.of[IO, Seq[ChatEvent]](Seq.empty)
    } yield new ChatEventStorage(ref)
  }
}
