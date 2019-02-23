package hello.todo

import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.server.middleware._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.rho._
import org.http4s.rho.swagger.{SwaggerSupport, SwaggerSyntax}
import doobie._

trait Routes {
  def getXA(implicit shift: ContextShift[IO]): IO[Transactor[IO]] = {
    for {
      database <- IO(sys.env.getOrElse("postgresDatabase", "postgres"))
      jdbc <- IO {
        sys.env.get("postgresEndpoint") match {
          case Some(endPoint) => s"jdbc:postgresql://$endPoint/postgres"
          case _ => s"jdbc:postgresql:$database"
        }
      }
      user <- IO(sys.env.getOrElse("postgresUser", "postgres"))
      pass <- IO(sys.env.getOrElse("postgresPassword", "postgres"))
    } yield Transactor.fromDriverManager[IO](
              "org.postgresql.Driver", // driver classname
              jdbc,             // connect URL (driver-specific)
              user,             // user
              pass              // password
           )
  }
  def routes(implicit concurrent: Concurrent[IO], shift: ContextShift[IO]): IO[HttpApp[IO]] = {
    for {
      _ <- IO(println("Setting up routes"))
      xa <- getXA
      todosAlg = new TodoDB[IO](xa)
      swaggerMiddleware = SwaggerSupport[IO].createRhoMiddleware(
        basePath = sys.env.get("STAGE").map(stage => s"/$stage/")
      )
      rhoRoutes: HttpRoutes[IO] = TodoRhoService[IO](todosAlg).toRoutes(swaggerMiddleware)
      migrationService: HttpRoutes[IO] = MigrationService[IO](xa)
    } yield CORS((rhoRoutes <+> migrationService).orNotFound)
  }
}

object HttpEntryPoint extends IOApp with Routes {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      r <- routes
      exitCode <- BlazeServerBuilder[IO]
        .bindHttp(8080, "localhost")
        .withHttpApp(r)
        .resource
        .use(_ => IO.never)
        .as(ExitCode.Success)
    } yield exitCode
  }
}

class LambdaEntryPoint extends LambdaHandler with Routes {
  def run: IO[HttpApp[IO]] = routes
}
