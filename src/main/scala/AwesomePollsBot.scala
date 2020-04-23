import canoe.api._
import canoe.methods.messages.SendPoll
import canoe.models.Update
import canoe.syntax._
import canoe.models.messages.TextMessage
import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp, Timer}
import cats.syntax.functor._
import cats.effect.concurrent.Ref
import fs2.Stream

sealed trait UserState
case class CreatePoll(question: Option[String] = None, options: List[String] = List()) extends UserState

class AwesomePollsBot(val token: String) {
  val questionMessage: String = "Enter question."
  val optionMessage: String = "Enter option."

  def run(implicit ec: ConcurrentEffect[IO], timer: Timer[IO]): IO[Stream[IO, Update]] = for {
    users <- Ref[IO].of(Map[Long, UserState]())
  } yield Stream
    .resource(TelegramClient.global[IO](token))
    .flatMap { implicit client => Bot.polling[IO].follow(poll(users), done(users), onMessage(users)) }

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