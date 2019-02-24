package hello.todo

import cats.effect._
import cats.implicits._
import org.http4s.EntityDecoder
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax

class TestService[F[+_]: Sync: ContextShift]
  extends RhoRoutes[F]
  with SwaggerSyntax[F] {

  import org.http4s.scalaxml._
  "XML Test" **
  GET / "test-xml" |>> {
    Ok(
      <books>
        <book id="b1615">Don Quixote</book>
        <book id="b1867">War and Peace</book>
      </books>
    )
  }

  import org.http4s._
  GET / "binary-test" |>> { (r: Request[F]) =>
    StaticFile.fromResource("/TTL-Logo.jpeg", scala.concurrent.ExecutionContext.global, r.some).getOrElseF(NotFound().map(_.resp))
  }
}
