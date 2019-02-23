package hello.todo

import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import doobie._
import doobie.implicits._
import doobie.syntax._

import org.http4s._
import org.http4s.dsl.Http4sDsl

object MigrationService {
  def apply[F[_]: Sync](xa: Transactor[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F];import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "migrate" =>
        sql"""CREATE TABLE public.todos
        (
           id BIGSERIAL PRIMARY KEY,
           msg text NOT NULL,
           is_completed boolean  NOT NULL
        )""".update.run.transact(xa) *>
        Ok()
    }
  }
}
