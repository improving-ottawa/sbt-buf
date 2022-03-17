

version := "0.0.1-SNAPSHOT"
scalaVersion := "2.13.7"
name := "TestSbtBufHappyPath"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.2",
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
)


Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
  scalapb.validate.gen() -> (Compile / sourceManaged).value / "scalapb"
)
