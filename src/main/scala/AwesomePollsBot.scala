import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.declarative.{Callbacks, Commands}
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.methods.{SendPoll, StopPoll}
import com.bot4s.telegram.models.ChatId

import scala.collection.mutable.{ListBuffer, Map}
import scala.concurrent.Future
import scala.util.Failure

// TODO
sealed trait UserState {
  object StartPoll extends UserState
  //case class FillPoll(question: String, options: Array[String], )
}

class AwesomePollsBot(token: String) extends Bot(token)
  with Polling
  with Commands[Future]
  with Callbacks[Future] {

  val userStates: Map[Long, UserState] = Map()

  val polls: ListBuffer[Int] = ListBuffer()

  onCommand("poll") { implicit msg =>
    val f = request(SendPoll(ChatId(msg.chat.id), "Pick A or B", Array("A", "B")))
    f.onComplete {
      case Failure(e) => logger.error(e.getMessage)
      case _ =>
    }
    for {
      poll <- f
    } yield {
      poll.poll match {
        case Some(value) => {
          logger.info(s"Poll with id ${value.id} sent.")
          polls += poll.messageId
        }
        case None => logger.error("The poll is None.")
      }
    }
  }

  onCommand("stop") { implicit msg =>
    request(StopPoll(ChatId(msg.chat.id), Some(polls.last))).void
  }
}
