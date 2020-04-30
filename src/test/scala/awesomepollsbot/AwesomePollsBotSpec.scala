package awesomepollsbot

import awesomepollbot.{AwesomePollsBot, CreatePoll, UserState}
import canoe.api.{Scenario, TelegramClient}
import canoe.methods.Method
import canoe.models.PrivateChat
import canoe.models.messages.{TelegramMessage, TextMessage}
import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Ref
import fs2.Stream
import org.scalamock.scalatest.proxy.MockFactory
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContextExecutor

class AwesomePollsBotSpec extends AnyFlatSpec with MockFactory with Matchers{
  implicit val timer: Timer[IO] = mock[Timer[IO]]
  implicit val tgClient: TelegramClient[IO] =
  new TelegramClient[IO] {
    override def execute[Req, Res](request: Req)(implicit M: Method[Req, Res]): IO[Res] = fail("Telegram requests should not be called.")
  }
  val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val cs: ContextShift[IO] = cats.effect.IO.contextShift(ec)
  implicit val sendMessages: Boolean = false

  def testScenario(sc: Ref[IO, Map[Long, UserState]] => Scenario[IO, Unit], messages: Stream[IO, TelegramMessage], check: Map[Long, UserState] => Assertion): Unit = {
    def testSc(users: Ref[IO, Map[Long, UserState]]): Scenario[IO, Unit] =
      for {
        _ <- sc(users)
        _  <- Scenario.eval(users.get)
      } yield ()
    (for {
      users <- Ref[IO].of(Map[Long, UserState]())
      _ <- messages.through(testSc(users).pipe).compile.drain
      result <- users.get
    } yield check(result)).unsafeRunSync()
  }

  "poll" should "set user's state to CreatePoll with command poll" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "/poll")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.poll, messages, _.get(userMessage.chat.id) should matchPattern {
      case Some(CreatePoll(None, List())) =>
    })
  }

  it should "ignore usual messages" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "Hello")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.poll, messages, _.get(userMessage.chat.id) should matchPattern {
      case None =>
    })
  }
}
