import slogging.{LazyLogging, LoggerConfig}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App with LazyLogging {
  LoggerConfig.factory = BotLogger.loggerFactory
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