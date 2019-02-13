import java.util.UUID

import io.circe._
import io.circe.generic.semiauto._
import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Ref
import org.http4s.{EntityDecoder, Request}
import org.http4s.server.middleware._
import org.http4s.rho._
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.{SwaggerSupport, SwaggerSyntax}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.rho.bits.{QueryParser, StringParser}

case class Todo(msg: String, isCompleted: Boolean = false)

trait TodoAlg[F[_]] extends  {
  def delete(id: UUID): F[Option[Unit]]
  def getAll: F[Map[UUID, Todo]]
  def add(todo: Todo): F[UUID]
  def complete(id: UUID): F[Option[Todo]]
}

object TodoAlg {
  def apply[F[_]: Sync](): F[TodoAlg[F]] =
    for {
      todos <- Ref.of[F, Map[UUID, Todo]](Map.empty)
    } yield new TodoAlg[F] {
      override def delete(id: UUID): F[Option[Unit]] =
        todos.modify { todos =>
          todos.get(id).map(_.copy(isCompleted = true)) match {
            case Some(todo) => (todos - id) -> Option(())
            case None => todos -> Option.empty
          }
        }
      override def getAll: F[Map[UUID, Todo]] = todos.get
      override def add(todo: Todo): F[UUID] = todos.modify { todosOld =>
        val uuid = UUID.randomUUID()
        todosOld.updated(uuid, todo) -> uuid
      }
      override def complete(id: UUID): F[Option[Todo]] =
        todos.modify { todos =>
          todos.get(id).map(_.copy(isCompleted = true)) match {
            case Some(todo) => todos.updated(id, todo) -> Option(todo)
            case None => todos -> Option.empty
          }
        }
    }
}

class TodoRho[F[+_]: Sync](todos: TodoAlg[F])
  extends RhoRoutes[F] with SwaggerSyntax[F]  {

  implicit val todoEncoder: Encoder[Todo] = deriveEncoder[Todo]
  implicit val todoDecoder: Decoder[Todo] = deriveDecoder[Todo]

  "List Todos" **
  GET / "todos" |>> {
    todos.getAll
      .flatMap { todosNow =>
      Ok(todosNow)
    }
  }

  "Create todo" **
  POST / "todo" ^ EntityDecoder[F, Todo] |>> {
    (newTodo: Todo) =>
      todos.add(newTodo).flatMap(uuid => Ok(uuid))
  }

  "Complete todo" **
  PUT / "todo" / pathVar[UUID]("id") |>> {
    (id: UUID) =>
      todos.complete(id).flatMap {
        case Some(todo) => Ok(todo)
        case None => NotFound(())
      }
  }

  "Delete Todo" **
  DELETE / "todo" / pathVar[UUID]("id") |>> {
    (id: UUID) =>
      todos.delete(id).flatMap {
        case Some(todo) => Ok(())
        case None => NotFound(())
      }
  }
}

object HelloHttp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val swaggerMiddleware = SwaggerSupport[IO].createRhoMiddleware()
    for {
      todosRef <- TodoAlg[IO]()
      rhoRoutes = new TodoRho[IO](todosRef).toRoutes(swaggerMiddleware)
      serve <- BlazeServerBuilder[IO]
        .bindHttp(8080, "localhost")
        .withHttpApp(CORS(rhoRoutes.orNotFound))
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield serve
  }
}
