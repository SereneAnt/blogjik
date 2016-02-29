name := "blogjik"

organization := "com.mycompany"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.7"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  // Use default sbt/maven layout
  // See https://www.playframework.com/documentation/2.4.x/Anatomy#Default-SBT-layout
  .disablePlugins(PlayLayoutPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.strikead.boreas"
  )

// Make controllers injectable
// See https://www.playframework.com/documentation/2.4.x/ScalaDependencyInjection#Injected-routes-generator
routesGenerator := InjectedRoutesGenerator

val slickPgVersion = "0.10.2"
val playSlickVersion = "1.1.1"  // This pulls Slick 3.1.x as a transitive dependency

//Placeholder for non test dependencies
libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "9.2-1004-jdbc41", // XXX: Should it be in provided scope instead?

  "com.github.tminglei" %% "slick-pg" % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_date2" % slickPgVersion,

  "com.typesafe.play" %% "play-slick" % playSlickVersion,
  "com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,

  "org.julienrf" %% "play-json-variants" % "2.0",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.10.43",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.14",
  "org.apache.httpcomponents" % "httpasyncclient" % "4.1.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.5.3",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr353" % "2.5.4",
  "io.dropwizard.metrics" % "metrics-healthchecks" % "3.1.1",
  ws
)

//Test dependencies
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalatestplus" %% "play" % "1.4.0-M3" % "test",
  "org.scalacheck" %% "scalacheck" % "1.12.5" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)


// Explicitly specify dependency versions (not the dependencies themselves)
// to avoid sbt eviction warnings.
// See http://www.scala-sbt.org/0.13/docs/Library-Management.html#Overriding+a+version

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.5.4",
  "com.google.guava" % "guava" % "18.0",
  "com.typesafe" % "config" % "1.3.0",
  "io.netty" % "netty" % "3.10.4.Final",
  "org.scala-lang" % "scala-library" % "2.11.7",
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.4",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "junit" % "junit" % "4.12"
)

// Scalatest options:
// D - show durations
// F - show full stack traces
testOptions in Test += Tests.Argument("-oDF")


excludeDependencies += "commons-logging"
