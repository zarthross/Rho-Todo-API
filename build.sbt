
scalaVersion := "2.12.8"
name := "http4s-todo-lambda"
assemblyJarName in assembly := "http4s-todo-lambda.jar"

scalacOptions += "-Ypartial-unification"

val http4sVersion = "0.20.0-M6"
val rhoVersion = "0.19.0-M6"
val http4Lambda = "0.4.0-M6"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "rho-swagger" % rhoVersion,
  "io.hydrosphere" %% "typed-sql" % "0.1.0",
  "org.tpolecat" %% "doobie-postgres" % "0.6.0",
  "org.slf4j" % "slf4j-simple" % "1.7.25",


	"org.scala-lang.modules" %% "scala-xml" % "1.1.1",
  "org.http4s" %% "http4s-scala-xml" % http4sVersion,
)

val circeVersion = "0.10.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0-M1")
