/*
For reference
 */

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.ScalajHttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import slogging.{LazyLogging, LogLevel, LoggerConfig, PrintLoggerFactory, StrictLogging}

import scala.util.Try
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/** Generates random values.
 */
class RandomBot(val token: String) extends TelegramBot
  with Polling
  with Commands[Future] {

  override val client: RequestHandler[Future] = new ScalajHttpClient(token)

  val rng = new scala.util.Random(System.currentTimeMillis())
  onCommand("coin" or "flip") { implicit msg =>
    reply(if (rng.nextBoolean()) "Head!" else "Tail!").void
  }
  onCommand('real | 'double | 'float) { implicit msg =>
    reply(rng.nextDouble().toString).void
  }
  onCommand("dice" | "roll") { implicit msg =>
    reply("⚀⚁⚂⚃⚄⚅" (rng.nextInt(6)).toString).void
  }
  onCommand("random" or "rnd") { implicit msg =>
    withArgs {
      case Seq(Int(n)) if n > 0 =>
        reply(rng.nextInt(n).toString).void
      case _ => reply("Invalid argumentヽ(ಠ_ಠ)ノ").void
    }
  }
  onCommand('choose | 'pick | 'select) { implicit msg =>
    withArgs { args =>
      replyMd(if (args.isEmpty) "No arguments provided." else args(rng.nextInt(args.size))).void
    }
  }

  // Int(n) extractor
  object Int {
    def unapply(s: String): Option[Int] = Try(s.toInt).toOption
  }
}

object Main extends App with LazyLogging {
  PrintLoggerFactory.infoStream = System.out
  PrintLoggerFactory.debugStream = System.out
  PrintLoggerFactory.traceStream = System.out
  LoggerConfig.factory = PrintLoggerFactory()
  // To run spawn the bot
  sys.env.get("TOKEN") match {
    case Some(value) => {
      val bot = new RandomBot(value)
      val eol = bot.run()
      logger.info("Press [ENTER] to shutdown the bot")
      scala.io.StdIn.readLine()
      logger.info("Started shutdown, it may take a few seconds...")
      bot.shutdown() // initiate shutdown
      // Wait for the bot end-of-life
      Await.result(eol, Duration.Inf)
    }
    case None => logger.error("No token provided")
  }
}