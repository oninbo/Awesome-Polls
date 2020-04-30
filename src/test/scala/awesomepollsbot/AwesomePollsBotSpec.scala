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

  "poll" should "set user's state to CreatePoll with command poll" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "/poll")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.poll, messages, _.get(userMessage.chat.id) should matchPattern {
      case Some(CreatePoll(None, List())) =>
    }, Map())
  }

  it should "ignore usual messages" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "Hello")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.poll, messages, _.get(userMessage.chat.id) should matchPattern {
      case None =>
    }, Map())
  }

  "done" should "remove user" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "/done")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.done, messages, _.get(userMessage.chat.id) should matchPattern {
      case None =>
    }, Map(0.longValue -> CreatePoll(Some("q"), List("1", "2"))))
  }

  it should "not remove user if there are not enough options" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "/done")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.done, messages, _.get(userMessage.chat.id) should matchPattern {
      case Some(CreatePoll(Some("q"), List("1"))) =>
    }, Map(0.longValue -> CreatePoll(Some("q"), List("1"))))
  }

  "onMessage" should "add question" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "q")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.onMessage, messages, _.get(userMessage.chat.id) should matchPattern {
      case Some(CreatePoll(Some("q"), List())) =>
    }, Map(0.longValue -> CreatePoll(None, List())))
  }

  it should "add option" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "1")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.onMessage, messages, _.get(userMessage.chat.id) should matchPattern {
      case Some(CreatePoll(Some("q"), List("1"))) =>
    }, Map(0.longValue -> CreatePoll(Some("q"), List())))
  }

  it should "ignore commands" in {
    val userMessage = TextMessage(0, PrivateChat(0, None, None, None), 0, "/q")
    val messages: Stream[IO, TelegramMessage] = Stream(userMessage)
    testScenario(AwesomePollsBot.onMessage, messages, _.get(userMessage.chat.id) should matchPattern {
      case Some(CreatePoll(Some("q"), List())) =>
    }, Map(0.longValue -> CreatePoll(Some("q"), List())))
  }

  def testScenario(sc: Ref[IO, Map[Long, UserState]] => Scenario[IO, Unit], messages: Stream[IO, TelegramMessage], check: Map[Long, UserState] => Assertion, users: Map[Long, UserState]): Unit = {
    def testSc(users: Ref[IO, Map[Long, UserState]]): Scenario[IO, Unit] =
      for {
        _ <- sc(users)
        _  <- Scenario.eval(users.get)
      } yield ()
    (for {
      usersRef <- Ref[IO].of(users)
      _ <- messages.through(testSc(usersRef).pipe).compile.drain
      result <- usersRef.get
    } yield check(result)).unsafeRunSync()
  }
}
