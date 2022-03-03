
name := """sbt-buf"""
organization := "com.cpc.tracktrace"
version := "0.1-SNAPSHOT"

sbtPlugin := true

// choose a test framework

// ScalaTest
libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.9" % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % "test"
val CirceVersion = "0.14.1"
inThisBuild(List(
  organization := "com.cpc.tracktrace",
  homepage := Some(url("https://github.com/sbt/sbt-autoplugin.g8")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.0"),
  libraryDependencies ++= Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % "0.11.8",
    "io.circe" %% "circe-core" % CirceVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-yaml" % CirceVersion
  ),
  developers := List(
    Developer(
      "dkichler",
      "Dave Kichler",
      "dave.kichler@yoppworks.com",
      url("https://yoppworks.com")
    )
  )
))

console / initialCommands := """import com.cpc.tracktrace.sbt._"""

enablePlugins(ScriptedPlugin)
// set up 'scripted; sbt plugin for testing sbt plugins
scriptedLaunchOpts ++=
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)

//ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
//ThisBuild / githubWorkflowPublishTargetBranches :=
//  Seq(RefPredicate.StartsWith(Ref.Tag("v")))


