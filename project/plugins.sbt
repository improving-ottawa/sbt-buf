libraryDependencies += "org.scala-sbt" %% "scripted-plugin"    % sbtVersion.value
addSbtPlugin("org.scalameta"            % "sbt-scalafmt"       % "2.4.6")
addSbtPlugin("com.dwijnand"             % "sbt-dynver"         % "4.1.1")
addSbtPlugin("no.arktekk.sbt"           % "aether-deploy"      % "0.27.0")
addSbtPlugin("com.codecommit"           % "sbt-github-actions" % "0.14.2")
// TODO: enable publishing
//addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")
