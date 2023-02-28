import sbt.Keys.scalaVersion
import sbt.util
import com.yoppworks.sbt.SbtBufPlugin.autoImport.Buf._

ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.7"

val scalaPbDeps =  Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.3",
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
)

lazy val external = project.in(file("./external"))
  .settings(
    name := "TestSbtBufHappyPathExternal",
    libraryDependencies ++= scalaPbDeps,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

lazy val api = project.in(file("./api"))
  .settings(
    name := "TestSbtBufHappyPathApi",
    libraryDependencies ++= scalaPbDeps,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )
  .dependsOn(
    kernel % "compile->compile;protobuf->protobuf"
  )

lazy val kernel = project.in(file("./kernel"))
  .settings(
    name := "TestSbtBufHappyPathKernel",
    libraryDependencies ++= scalaPbDeps,
    // Deliberately adding a delay to ensure that the Buf images are generated with the correct order of dependencies even though the Kernel module takes longer to generate Buf images
    generateBufImage := (generateBufImage dependsOn testDelayTask).value,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

/** Contains all possible protobuf dependency types: external module (mimicing another library), cross-module (against `api`) */
lazy val client = project.in(file("./client"))
  .settings(
    name := "TestSbtBufHappyPathClient",
    libraryDependencies ++= scalaPbDeps,
    libraryDependencies += "testsbtbufhappypathexternal" %% "testsbtbufhappypathexternal" % "0.0.1-SNAPSHOT" % "compile;protobuf",
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )
  .dependsOn(
    api % "compile->compile;protobuf->protobuf",
    kernel % "compile->compile;protobuf->protobuf"
  )

val testEmptySourcesTask = TaskKey[Unit]("testEmptySourcesTask", "Task for asserting root module reports as containing no Buf/protobuf sources")
/** Contains no proto sources or dependencies */
lazy val service = project.in(file("."))
  .settings(
    name := "TestSbtHappyPathService",
    libraryDependencies ++= scalaPbDeps,
    libraryDependencies += "testsbtbufhappypathexternal" %% "testsbtbufhappypathexternal" % "0.0.1-SNAPSHOT" % "compile",
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    testEmptySourcesTask := {
      require(!Buf.hasBufSrcs.value, "Module should report no protobuf/Buf sources")
      ()
    }
  )
  .dependsOn(api % "compile->compile")
  .aggregate(kernel, api, client)

TaskKey[Unit]("checkEvicted") := {
  require(evicted.value.allEvictions.isEmpty, "Eviction notices should be empty")
  ()
}
