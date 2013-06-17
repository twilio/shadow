import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import AssemblyKeys._
import sbtrelease.ReleaseStep
import spray.revolver.RevolverPlugin._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._

object ShadowBuild extends Build {

  lazy val shadow = Project(
    id = "shadow",
    base = file("."),
    settings = Project.defaultSettings ++ releaseSettings ++ Revolver.settings ++ assemblySettings ++ Seq(
      name := "shadow",
      organization := "com.twilio",
      scalaVersion := "2.10.0",
      mainClass in assembly := Some("com.twilio.shadow.Boot"),
      assembleArtifact in packageScala := true, //exclude scala library from assembly artifact
      test in assembly := {}, //disable running test when creating assembly aritifact
      assemblyCacheOutput in assembly := true,

      libraryDependencies ++= List(

        "io.spray" % "spray-can" % "1.1-M7",
        "io.spray" % "spray-routing" % "1.1-M7",
        "io.spray" % "spray-testkit" % "1.1-M7",
        "com.typesafe.akka" %% "akka-actor" % "2.1.0",
        "org.json4s" %% "json4s-native" % "3.2.4",
        "org.json4s" %% "json4s-jackson" % "3.2.4",
        "com.codahale.metrics" % "metrics-core" % "3.0.0-BETA2",
        "com.codahale.metrics" % "metrics-json" % "3.0.0-BETA2",

        // log generation
        "org.slf4j" % "slf4j-api" % "1.7.5",
        "ch.qos.logback" % "logback-classic" % "1.0.13",

        // test
        "org.scalatest" %% "scalatest" % "2.0.M5b" % "test",
        "com.xebialabs.restito" % "restito" % "0.4-alpha-2" % "test",
        "com.typesafe.akka" %% "akka-testkit" % "2.1.0" % "test"

      ),
      compileOrder := CompileOrder.Mixed,
      mergeStrategy in assembly <<= (mergeStrategy in assembly) {
        (old) => {
          case x => old(x)
        }
      },
      classpathTypes ~= (_ + "orbit"),
      resolvers ++= List(
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "spray repo" at "http://repo.spray.io"
      )
    )
  )
}
