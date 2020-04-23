import pureconfig._
import pureconfig.generic.auto._
import cats.effect.{ExitCode, IO, IOApp}

case class BotConfig(token: String)

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val config = ConfigSource.default.load[BotConfig]
    config match {
      case Left(_) => IO(ExitCode.Error)
      case Right(value) => for {
        bot <- new AwesomePollsBot(value.token).run
        _ <- bot.compile.drain
      } yield ExitCode.Success
    }
  }
}