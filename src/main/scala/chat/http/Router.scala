package chat.http

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*

import chat.model.User
import chat.service.ChatService

final class Router(
    chat: ChatService,
    ws: WebSocketChat
) {
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health"        => Ok("ok")
    case GET -> Root / "ws" / username => ws.connect(User(username))
    case GET -> Root / "users"         => {
      chat
        .getUsers()
        .flatMap(users => Ok(users.map(_.name).mkString(", ")))
    }
  }
}
