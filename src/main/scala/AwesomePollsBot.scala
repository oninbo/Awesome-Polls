import canoe.api._
import canoe.methods.messages.SendPoll
import canoe.syntax._
import canoe.models.messages.TextMessage
import cats.effect.{ExitCode, IO, IOApp, Timer}
import cats.syntax.functor._
import fs2.Stream

import scala.collection.mutable

sealed trait UserState
case class CreatePoll(question: Option[String] = None, options: List[String] = List()) extends UserState

class AwesomePollsBot(val token: String) extends IOApp {
  val userStates: mutable.Map[Long, UserState] = mutable.Map()

  val questionMessage: String = "Enter question."
  val optionMessage: String = "Enter option. Use /done to create a poll."

  def run(args: List[String]): IO[ExitCode] =
    Stream
      .resource(TelegramClient.global[IO](token))
      .flatMap { implicit client => Bot.polling[IO].follow(poll, done, onMessage) }
      .compile.drain.as(ExitCode.Success)

  def poll[F[_]: TelegramClient: Timer]: Scenario[F, Unit] =
    for {
      message <- Scenario.expect(command("poll"))
      _ <- {
        userStates += (message.chat.id -> CreatePoll())
        Scenario.eval(message.chat.send(questionMessage))
      }
    } yield ()

  def onMessage[F[_]: TelegramClient: Timer]: Scenario[F, Unit] =
    for {
      message <- Scenario.expect {
        case m: TextMessage if !isCommand(m.text) => m
      }
      _ <- userStates.get(message.chat.id) match {
        case Some(CreatePoll(question, options)) => {
          question match {
            case Some(value) => {
              userStates += (message.chat.id -> CreatePoll(Some(value), options :+ message.text))
            }
            case None => {
              userStates += (message.chat.id -> CreatePoll(Some(message.text)))
            }
          }
          Scenario.eval(message.chat.send(optionMessage))
        }
        case _ => Scenario.done[F]
      }
    } yield ()


  def done[F[_]: TelegramClient: Timer]: Scenario[F, Unit] =
    for {
      message <- Scenario.expect(command("done"))
      _ <- userStates.get(message.chat.id) match {
        case Some(CreatePoll(question, options)) => {
          question match {
            case Some(value) => {
              if (options.length < 2) Scenario.eval(message.chat.send("Not enough options."))
              else {
                userStates -= message.chat.id
                Scenario.eval(SendPoll(message.chat.id, value, if (options.length > 10)
                  options.take(10) else options, Some(false)).call)
              }
            }
            case _ => Scenario.done[F]
          }
        }
        case _ => Scenario.done[F]
      }
    } yield ()

  def isCommand(s: String): Boolean = s.startsWith("/")
}