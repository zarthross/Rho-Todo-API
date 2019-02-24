package hello.todo

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import cats._
import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import fs2._
import scala.concurrent._
import scala.io.Source

object LambdaHandler {
  implicit val methodDecoder: Decoder[Method] = Decoder[String].emap { s =>
    Method.fromString(s).left.map(_.getMessage)
  }
}
import LambdaHandler._

trait LambdaHandler {
  protected implicit lazy val  defaultTimer: Timer[IO] = IO.timer(ExecutionContext.Implicits.global)
  protected implicit lazy val  defaultContextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  def run: IO[HttpApp[IO]]

  private val memoedRoutes: IO[HttpApp[IO]] = Concurrent.memoize(run).unsafeRunSync
  def handle(is: InputStream, os: OutputStream): Unit = {
    for {
      (request, routes) <- (inputStreamToRequest(is), memoedRoutes).parTupled
      response <- routes(request)
      _ <- responseToOutputStream(os)(response)
    } yield()
  }.unsafeRunSync

  private def inputStreamToRequest(is: InputStream): IO[Request[IO]] = {
    Resource.make(is.pure[IO]) { i => IO(i.close()) }
      .evalMap(is => IO(Source.fromInputStream(is).mkString))
      .use { s =>
        decode[ProxyRequest](s)
          .flatMap(_.toRequest[IO])
          .leftWiden[Throwable]
          .pure[IO]
      }
      .rethrow
  }

  private def responseToOutputStream(os: OutputStream)(response: Response[IO]): IO[Unit] = {
    Resource.make(os.pure[IO]) { i => IO(i.close()) }
      .use { os =>
        ProxyResponse.toResponse(response)
          .flatMap { proxyResponse =>
            IO {
              os.write(
                proxyResponse
                  .asJson.noSpaces
                  .getBytes(StandardCharsets.UTF_8)
              )
            }
          }
      }
  }
}

case class ProxyRequest(
    httpMethod: Method,
    path: String,
    headers: Option[Map[String, String]],
    multiValueHeaders: Option[Map[String, List[String]]],
    queryStringParameters: Option[Map[String, String]],
    multiValueQueryStringParameters: Option[Map[String, List[String]]],
    body: Option[String],
    isBase64Encoded: Boolean // For Binary Support
  ) {

  def uri: ParseResult[Uri] = {
    Uri.fromString(path).map { u: Uri =>
      multiValueQueryStringParameters.getOrElse(Map.empty)
        .foldLeft(u) { (a, kvps) =>
          a +?(kvps._1, kvps._2)
        }
    }
  }

  def toRequest[F[_]: Sync]: ParseResult[Request[F]] = uri.map { url =>
    Request[F](
      httpMethod,
      url,
      headers = headers.map(toHeaders).getOrElse(Headers.empty),
      body = body.map(encodeBody(isBase64Encoded)).getOrElse(EmptyBody)
    )
  }

  private def toHeaders(headers: Map[String, String]): Headers =
    Headers {
      headers.map {
        case (k, v) => Header(k, v)
      }.toList
    }

  private def encodeBody(isBase64Encoded: Boolean)(body: String) = {
    if(isBase64Encoded) {
      Stream.emits(java.util.Base64.getDecoder.decode(body))
    }
    else {
      Stream(body).through(text.utf8Encode)
    }
  }
}

case class ProxyResponse(
  statusCode: Int,
  headers: Map[String, String],
  body: String,
  isBase64Encoded: Boolean
)
object ProxyResponse {
  def toResponse[F[_]: Sync](resp: Response[F]): F[ProxyResponse] = {
    import org.http4s.headers._
    (resp.contentType match {
      case Some(contentType) if contentType.mediaType.binary =>
        resp.as[Array[Byte]]
          .map(java.util.Base64.getEncoder.encodeToString)
          .map(_ -> true)
      case _ => resp.as[String].map(_ -> false)
    })
    .map { case (body, enc) =>
      ProxyResponse(
          resp.status.code,
          headers = resp.headers
            .map(h => h.name.value -> h.value)
            .toMap,
          body,
          isBase64Encoded = enc)
    }
  }
}
