package hello.todo

import cats.effect._
import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.EntityDecoder
import org.http4s.circe.{CirceEntityEncoder,CirceEntityDecoder}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax

class TodoRhoService[F[+_]: Sync](todos: TodoAlg[F])
  extends RhoRoutes[F]
  with SwaggerSyntax[F]
  with CirceEntityEncoder 
  with CirceEntityDecoder {

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
    todos.add(newTodo)
      .flatMap { id =>
        Ok(id)
      }
  }

  "Complete todo" **
  PUT / "todo" / pathVar[Id]("id") |>> {
    (id: Id) =>
      todos.complete(id).flatMap {
        case Some(todo) => Ok(todo)
        case None => NotFound(())
      }
  }

  "Delete Todo" **
  DELETE / "todo" / pathVar[Id]("id") |>> {
    (id: Id) =>
      todos.delete(id).flatMap {
        case Some(todo) => Ok(())
        case None => NotFound(())
      }
  }
}
object TodoRhoService {
  def apply[F[+ _] : Sync](todos: TodoAlg[F]) = new TodoRhoService[F](todos)
}