import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

// mainClass in Compile := Some("Main")


lazy val root = (project in file("."))
  .settings(
    name := "scala",
    libraryDependencies += munit % Test
  )

resolvers += "Akka library repository".at("https://repo.akka.io/maven")


val AkkaVersion = "2.9.0"
val AkkaHttpVersion = "10.6.0"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.6"
)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
