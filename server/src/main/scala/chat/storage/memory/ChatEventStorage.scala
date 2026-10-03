package chat.storage.memory

import cats.effect.{IO, Ref}

import chat.model.ChatEvent
import chat.storage.{ChatEventFilter, Storage}

final class ChatEventStorage(events: Ref[IO, Seq[ChatEvent]]) extends Storage.ChatEvents:
  override def save(event: ChatEvent): IO[Unit] = events.update(_.appended(event))

  override def list(filter: ChatEventFilter): IO[Seq[ChatEvent]] = events.get.map { events =>
    val filtered = filter.user match
      case Some(user) => events.filter {
          case _: ChatEvent.Broadcast         => true
          case event: ChatEvent.DirectMessage => event.sender == user || event.recipient == user
          case _                              => false
        }
      case None => events

    filter.limit match
      case Some(limit) => filtered.takeRight(limit)
      case None        => filtered
  }

object ChatEventStorage:

  def build: IO[ChatEventStorage] =
    for ref <- Ref.of[IO, Seq[ChatEvent]](Seq.empty) yield new ChatEventStorage(ref)
