inThisBuild(List(
  scalaVersion := "3.9.0",
  organization := "otus",
  version := "0.1",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalacOptions += "-Wunused:imports"
))

lazy val catsEffectVersion = "3.7.1"
lazy val http4sVersion = "0.23.37"
lazy val http4sJdkClientVersion = "0.10.0"
lazy val circeVersion = "0.14.14"
lazy val log4catsVersion = "2.8.0"
lazy val logbackVersion = "1.6.3"
lazy val jlineVersion = "3.26.0"
lazy val munitVersion = "1.0.0"
lazy val munitCatsEffectVersion = "2.0.0"

lazy val root = (project in file(".")).aggregate(server, client, shared)
  .settings(name := "campfire", publish / skip := true)

lazy val shared = (project in file("shared")).settings(
  name := "campfire-shared",
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
)

lazy val server = (project in file("server")).dependsOn(shared).settings(
  name := "campfire-server",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime,
    "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test
  )
)

lazy val client = (project in file("client")).dependsOn(shared).settings(
  name := "campfire-client",
  Compile / run / fork := true,
  Compile / run / connectInput := true,
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "org.http4s" %% "http4s-jdk-http-client" % http4sJdkClientVersion,
    "org.jline" % "jline" % jlineVersion,
    "org.scalameta" %% "munit" % munitVersion % Test
  )
)
