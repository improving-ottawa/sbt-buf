import sbt.Keys.organization

sbtPlugin := true

val CirceVersion = "0.14.1"
inThisBuild(List(
  name := """sbt-buf""",
  organization := "com.yoppworks",
  version := "0.1-SNAPSHOT",
  homepage := Some(url("https://github.com/sbt/sbt-autoplugin.g8")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.0"),
  libraryDependencies ++= Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % "0.11.9",
    "io.circe" %% "circe-core" % CirceVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-yaml" % CirceVersion,
    "org.scalactic" %% "scalactic" % "3.2.9" % Test,
    "org.scalatest" %% "scalatest" % "3.2.9" % Test
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


