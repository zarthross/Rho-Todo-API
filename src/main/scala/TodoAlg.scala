package hello.todo

import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Ref

case class Todo(msg: String, isCompleted: Boolean = false)

trait TodoAlg[F[_]] extends  {
  def delete(id: Id): F[Option[Unit]]
  def getAll: F[Map[Id, Todo]]
  def add(todo: Todo): F[Id]
  def complete(id: Id): F[Option[Todo]]
}

object TodoAlg {
  def apply[F[_]: Sync](): F[TodoAlg[F]] =
    for {
      todos <- Ref.of[F, Map[Id, Todo]](Map.empty)
    } yield new TodoAlg[F] {
      override def delete(id: Id): F[Option[Unit]] =
        todos.modify { todos =>
          todos.get(id).map(_.copy(isCompleted = true)) match {
            case Some(todo) => (todos - id) -> Option(())
            case None => todos -> Option.empty
          }
        }
      override def getAll: F[Map[Id, Todo]] = todos.get
      override def add(todo: Todo): F[Id] = todos.modify { todosOld =>
        val id = scala.util.Random.nextLong()
        todosOld.updated(id, todo) -> id
      }
      override def complete(id: Id): F[Option[Todo]] =
        todos.modify { todos =>
          todos.get(id).map(_.copy(isCompleted = true)) match {
            case Some(todo) => todos.updated(id, todo) -> Option(todo)
            case None => todos -> Option.empty
          }
        }
    }
}
