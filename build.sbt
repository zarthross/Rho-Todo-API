
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % "0.20.0-M4",
  "org.http4s" %% "http4s-circe" % "0.20.0-M4",
  "org.http4s" %% "rho-swagger" % "0.19.0-M4"
)

val circeVersion = "0.10.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)