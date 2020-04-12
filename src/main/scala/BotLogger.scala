import slogging.LogLevel.{INFO, TRACE}
import slogging.{LoggerConfig, PrintLoggerFactory, UnderlyingLoggerFactory}

object BotLogger {
  val loggerFactory: UnderlyingLoggerFactory = {
    PrintLoggerFactory.infoStream = System.out
    PrintLoggerFactory.debugStream = System.out
    PrintLoggerFactory.traceStream = System.out
    LoggerConfig.level = INFO
    PrintLoggerFactory()
  }
}