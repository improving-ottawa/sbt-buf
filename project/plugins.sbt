libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")

addSbtPlugin("no.arktekk.sbt"    % "aether-deploy"       % "0.27.0")
// TODO: enable publishing
//addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")

