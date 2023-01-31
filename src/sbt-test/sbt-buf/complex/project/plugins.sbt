{
  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.yoppworks" % """sbt-buf""" % pluginVersion)
}
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.4")
libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin" % "0.11.8",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.2"
)
