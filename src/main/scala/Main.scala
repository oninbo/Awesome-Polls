import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = for {
    token <- IO(sys.env("TOKEN"))
    _ <- new AwesomePollsBot(token).run(args)
  } yield ExitCode.Success
}