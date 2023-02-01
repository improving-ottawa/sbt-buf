import sbt.Keys.scalaVersion

import scala.language.postfixOps

ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.7"

val scalaPbDeps =  Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.3",
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
)

import com.yoppworks.sbt.BreakingUse
import com.yoppworks.sbt.BreakingUse._

lazy val root = project.in(file("./"))
  .settings(
    name := "SimpleExample",
    libraryDependencies ++= scalaPbDeps,
    //Buf.breakingCategory := Seq(BreakingUse.Package),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

TaskKey[Unit]("checkEvicted") := {
  require(evicted.value.allEvictions.isEmpty, "Eviction notices should be empty")
  ()
}
