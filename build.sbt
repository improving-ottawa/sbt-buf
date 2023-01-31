import sbt.Keys.{console, organization}

val CirceVersion = "0.14.1"
lazy val sbtBuf = project
  .in(file("."))
  .enablePlugins(ScriptedPlugin)
  .configure(Helpers.publishing)
  .settings(
    name         := """sbt-buf""",
    organization := "com.yoppworks",
    homepage     := Some(url("https://github.com/YoppWorks/sbt-buf")),
    licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    sbtPlugin := true,
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.0"),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % "0.11.11",
      "io.circe"             %% "circe-core"     % CirceVersion,
      "io.circe"             %% "circe-generic"  % CirceVersion,
      "io.circe"             %% "circe-yaml"     % CirceVersion,
      "org.scalactic"        %% "scalactic"      % "3.2.14" % Test,
      "org.scalatest"        %% "scalatest"      % "3.2.14" % Test
    ),
    developers := List(
      Developer(
        "dkichler",
        "Dave Kichler",
        "dave.kichler@yoppworks.com",
        url("https://yoppworks.com")
      )
    ),
    scalacOptions ++= Seq(
      "-Xfatal-warnings"
    ),
    console / initialCommands := "import com.yoppwork._",
    // set up 'scripted; sbt plugin for testing sbt plugins
    scriptedLaunchOpts ++=
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  )

// TODO:  enable publishing
//ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
//ThisBuild / githubWorkflowPublishTargetBranches :=
//  Seq(RefPredicate.StartsWith(Ref.Tag("v")))
