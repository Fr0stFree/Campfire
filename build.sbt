ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "otus"
ThisBuild / version := "0.1"

lazy val catsEffectVersion = "3.7.1"
lazy val http4sVersion = "0.23.37"
lazy val circeVersion = "0.14.14"
lazy val log4catsVersion = "2.8.0"
lazy val logbackVersion = "1.6.3"

lazy val root = (project in file("."))
  .aggregate(server, client, shared)
  .settings(
    name := "campfire",
    publish / skip := true
  )

lazy val shared = (project in file("shared"))
  .settings(
    name := "campfire-shared",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
  )

lazy val server = (project in file("server"))
  .dependsOn(shared)
  .settings(
    name := "campfire-server",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime
    )
  )

lazy val client = (project in file("client"))
  .dependsOn(shared)
  .settings(
    name := "campfire-client",
    libraryDependencies += "io.circe" %% "circe-parser" % circeVersion
  )
