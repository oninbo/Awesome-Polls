import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.clients.{ScalajHttpClient}
import com.bot4s.telegram.future.TelegramBot
import slogging.{LoggerConfig}

import scala.concurrent.Future

/** Base class bots.
 *
 * Mix Polling or Webhook accordingly.
 *
 * Example:
 * new EchoBot("123456789:qwertyuiopasdfghjklyxcvbnm123456789").run()
 *
 * @param token Bot's token.
 */
abstract class Bot(val token: String) extends TelegramBot {
  LoggerConfig.factory = BotLogger.loggerFactory

  override val client: RequestHandler[Future] = new ScalajHttpClient(token)

}
