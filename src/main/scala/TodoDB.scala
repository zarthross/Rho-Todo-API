package hello.todo

import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import doobie._
import doobie.implicits._
import doobie.syntax._

import typed.sql._
import typed.sql.syntax._
import typed.sql.toDoobie._

case class TodoRow(id: Id, msg: String, is_completed: Boolean = false) {
  def toTodo = id -> Todo(msg, is_completed)
}

class TodoDB[F[_]: Sync](xa: Transactor[F]) extends TodoAlg[F] {
  val todos = Table.of[TodoRow].autoColumn('id).name('todos)
  val id = todos.col('id)
  val msg = todos.col('msg)
  val isCompleted = todos.col('is_completed)

  def delete(id: Id): F[Option[Unit]] =
    typed.sql.syntax.delete.from(todos).where(this.id === id).toUpdate.run.map {
      _.some.filter(_ >= 1).void
    }.transact(xa)

  def getAll: F[Map[Id, Todo]] =
    select(*).from(todos).toQuery.to[List].transact(xa).map {
      _.map(_.toTodo).toMap
    }

  def add(todo: Todo): F[Id] =
    insert.into(todos).values(todo.msg, todo.isCompleted)
      .toUpdate.withUniqueGeneratedKeys[Id]("id").transact(xa)

  def complete(id: Id): F[Option[Todo]] =
    (for {
      _ <- update(todos).set(isCompleted := true).where(this.id === id).toUpdate.run
      todoRow <- select(*).from(todos).where(this.id === id).toQuery.option
    } yield todoRow.map(_.toTodo._2)).transact(xa)
}
