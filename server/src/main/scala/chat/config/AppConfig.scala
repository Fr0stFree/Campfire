package chat.config
import com.comcast.ip4s.*

final case class AppConfig(
    port: Port,
    host: Host
)

object AppConfig {
  def default: AppConfig = AppConfig(
    port = port"8080",
    host = ipv4"127.0.0.1"
  )

  def fromArgs(args: List[String]): Either[Throwable, AppConfig] = {
    val default = AppConfig.default
    for {
      port <- args.headOption match {
        case None          => Right(default.port)
        case Some(portStr) =>
          Port
            .fromString(portStr)
            .toRight(new Throwable(s"Invalid port: $portStr"))
      }
      host <- args.drop(1).headOption match {
        case None          => Right(default.host)
        case Some(hostStr) =>
          Host
            .fromString(hostStr)
            .toRight(new Throwable(s"Invalid host: $hostStr"))
      }
    } yield AppConfig(port, host)
  }
}
