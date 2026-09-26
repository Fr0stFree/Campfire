ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "otus"
ThisBuild / version := "0.1"

lazy val catsEffectVersion = "3.7.1"
lazy val http4sVersion = "0.23.37"
lazy val circeVersion = "0.14.14"

lazy val root = (project in file("."))
  .aggregate(server, client, shared)
  .settings(
    name := "scala-chat",
    publish / skip := true
  )

lazy val shared = (project in file("shared"))
  .settings(
    name := "scala-chat-shared",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )

lazy val server = (project in file("server"))
  .dependsOn(shared)
  .settings(
    name := "scala-chat-server",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-core" % circeVersion
    )
  )

lazy val client = (project in file("client"))
  .dependsOn(shared)
  .settings(
    name := "scala-chat-client",
    libraryDependencies += "io.circe" %% "circe-parser" % circeVersion
  )
