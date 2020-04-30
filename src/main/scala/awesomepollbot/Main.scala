package awesomepollbot

import cats.effect.{ExitCode, IO, IOApp}
import pureconfig._
import pureconfig.generic.auto._

case class BotConfig(token: String)

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val config = ConfigSource.default.load[BotConfig]
    config match {
      case Left(_) => IO(ExitCode.Error)
      case Right(value) =>
        for {
          bot <- AwesomePollsBot.run(value.token)
          _   <- bot.compile.drain
        } yield ExitCode.Success
    }
  }
}
