package awesomepollbot

import canoe.api._
import canoe.methods.messages.SendPoll
import canoe.models.Update
import canoe.models.messages.{TextMessage}
import canoe.syntax._
import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, IO, Timer}
import fs2.Stream

sealed trait UserState
case class CreatePoll(question: Option[String] = None, options: List[String] = List()) extends UserState

object AwesomePollsBot {
  val questionMessage: String = "Enter question."
  val optionMessage: String = "Enter option."
  implicit val sendMessages: Boolean = true

  def run(token: String)(implicit ec: ConcurrentEffect[IO], timer: Timer[IO]): IO[Stream[IO, Update]] = for {
    users <- Ref[IO].of(Map[Long, UserState]())
  } yield Stream
    .resource(TelegramClient.global[IO](token))
    .flatMap { implicit client => Bot.polling[IO].follow(poll(users), done(users), onMessage(users)) }

  def poll[F[_]: TelegramClient: Timer](users: Ref[F, Map[Long, UserState]])(implicit sendMessages: Boolean): Scenario[F, Unit] =
    for {
      message <- Scenario.expect(command("poll"))
      _ <- Scenario.eval(users.update(_ + (message.chat.id -> CreatePoll())))
      _ <- if (sendMessages) Scenario.eval(message.chat.send(questionMessage)) else Scenario.done[F]
    } yield ()

  def onMessage[F[_]: TelegramClient: Timer](users: Ref[F, Map[Long, UserState]])(implicit sendMessages: Boolean): Scenario[F, Unit] =
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
          _ <- if (sendMessages) Scenario.eval(message.chat.send(optionMessage)) else Scenario.done[F]
        } yield ()
        case _ => Scenario.done[F]
      }
    } yield ()


  def done[F[_]: TelegramClient: Timer](users: Ref[F, Map[Long, UserState]])(implicit sendMessages: Boolean): Scenario[F, Unit] =
    for {
      message <- Scenario.expect(command("done"))
      usersMap <- Scenario.eval(users.get)
      _ <- usersMap.get(message.chat.id) match {
        case Some(CreatePoll(question, options)) => {
          question match {
            case Some(value) => {
              if (options.length < 2) if (sendMessages) Scenario.eval(message.chat.send("Not enough options.")) else Scenario.done[F]
              else for {
                _ <- Scenario.eval(users.update(_ - message.chat.id))
                _ <- if (sendMessages) Scenario.eval(SendPoll(message.chat.id, value, if (options.length > 10)
                  options.take(10) else options, Some(false)).call) else Scenario.done[F]
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