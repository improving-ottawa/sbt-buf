import sbt.Keys.scalaVersion
import sbt.util

ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.7"

val scalaPbDeps =  Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.3",
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
)

lazy val external = project.in(file("./external"))
  .settings(
    name := "TestBufLocalPublishBugExternal",
    libraryDependencies ++= scalaPbDeps,
    // setting this to false prevents the image from being generated, but not from being added to the ivy manifest
    Buf.hasBufSrcs := false,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

val testEmptySourcesTask = TaskKey[Unit]("testEmptySourcesTask", "Task for asserting root module reports as containing no Buf/protobuf sources")
/** Contains no proto sources or dependencies */
lazy val service = project.in(file("."))
  .settings(
    name := "TestBufLocalPublishBug",
    libraryDependencies ++= scalaPbDeps,
    libraryDependencies += "testbuflocalpublishbugexternal" %% "testbuflocalpublishbugexternal" % "0.0.1-SNAPSHOT" % "compile;protobuf",
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )
