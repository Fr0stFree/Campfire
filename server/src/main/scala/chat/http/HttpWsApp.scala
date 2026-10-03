package chat.http

import cats.effect.IO
import org.http4s.dsl.io._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{HttpApp, HttpRoutes}
import org.typelevel.log4cats.Logger

import chat.model.User
import chat.service.ChatService

final class HttpWsApp(chat: ChatService, ws: WebSocketApp):

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health"        => Ok("ok")
    case GET -> Root / "ws" / username => ws.connect(User(username))
  }

object HttpWsApp:

  def build(service: ChatService, wsb: WebSocketBuilder2[IO])(using Logger[IO]): HttpApp[IO] =
    val ws = new WebSocketApp(wsb, service)
    new HttpWsApp(service, ws).routes.orNotFound
