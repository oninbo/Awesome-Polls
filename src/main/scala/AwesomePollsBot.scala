import canoe.api._
import canoe.methods.messages.SendPoll
import canoe.syntax._
import canoe.models.messages.TextMessage
import cats.effect.{ExitCode, IO, IOApp, Timer}
import cats.syntax.functor._
import cats.effect.concurrent.Ref
import fs2.Stream

import scala.collection.mutable

sealed trait UserState
case class CreatePoll(question: Option[String] = None, options: List[String] = List()) extends UserState

class AwesomePollsBot(val token: String) extends IOApp {
  val userStates: mutable.Map[Long, UserState] = mutable.Map()

  val questionMessage: String = "Enter question."
  val optionMessage: String = "Enter option."

  def run(args: List[String]): IO[ExitCode] = for {
    users <- Ref[IO].of(Map[Long, UserState]())
    result <- Stream
      .resource(TelegramClient.global[IO](token))
      .flatMap { implicit client => Bot.polling[IO].follow(poll(users), done(users), onMessage(users)) }
      .compile.drain.as(ExitCode.Success)
  } yield result

  def poll[F[_]: TelegramClient: Timer](users: Ref[F, Map[Long, UserState]]): Scenario[F, Unit] =
    for {
      message <- Scenario.expect(command("poll"))
      _ <- Scenario.eval(users.update(_ + (message.chat.id -> CreatePoll())))
      _ <- Scenario.eval(message.chat.send(questionMessage))
    } yield ()

  def onMessage[F[_]: TelegramClient: Timer](users: Ref[F, Map[Long, UserState]]): Scenario[F, Unit] =
    for {
      message <- Scenario.expect {
        case m: TextMessage if !isCommand(m.text) => m
      }
      usersMap <- Scenario.eval(users.get)
      _ <- usersMap.get(message.chat.id) match {
        case Some(CreatePoll(question, options)) => for {
          _ <- question match {
            case Some(value) => {
              Scenario.eval(users.update(_ + (message.chat.id -> CreatePoll(Some(value), options :+ message.text))))
            }
            case None => {
              Scenario.eval(users.update(_ + (message.chat.id -> CreatePoll(Some(message.text)))))
            }
          }
          _ <- Scenario.eval(message.chat.send(optionMessage))
        } yield ()
        case _ => Scenario.done[F]
      }
    } yield ()


  def done[F[_]: TelegramClient: Timer](users: Ref[F, Map[Long, UserState]]): Scenario[F, Unit] =
    for {
      message <- Scenario.expect(command("done"))
      usersMap <- Scenario.eval(users.get)
      _ <- usersMap.get(message.chat.id) match {
        case Some(CreatePoll(question, options)) => {
          question match {
            case Some(value) => {
              if (options.length < 2) Scenario.eval(message.chat.send("Not enough options."))
              else for {
                _ <- Scenario.eval(users.update(_ - message.chat.id))
                _ <- Scenario.eval(SendPoll(message.chat.id, value, if (options.length > 10)
                  options.take(10) else options, Some(false)).call)
              } yield ()
            }
            case _ => Scenario.done[F]
          }
        }
        case _ => Scenario.done[F]
      }
    } yield ()

  def isCommand(s: String): Boolean = s.startsWith("/")
}