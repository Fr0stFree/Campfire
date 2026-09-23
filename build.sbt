ThisBuild / scalaVersion := "3.9.0"

lazy val catsEffectVersion = "3.7.1"
lazy val http4sVersion = "0.23.37"
lazy val circeVersion = "0.14.14"

lazy val root = (project in file("."))
  .settings(
    name := "ws-chat",
    organization := "otus",
    version := "0.1",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
