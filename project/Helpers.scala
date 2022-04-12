import sbt.Keys._
import sbt._

/** Helpers For projects */
object Helpers {

  private val nexusBase = "http://internal-nexus.nexus:8081"

  private val nexusPublic =
    ("Innovapost Internal Public Maven" at nexusBase + "/repository/maven-public")
      .withAllowInsecureProtocol(true)

  val nexusSnapshots =
    ("Innovapost Internal Snapshots Nexus" at nexusBase + "/repository/maven-snapshots")
      .withAllowInsecureProtocol(true)

  val nexusReleases =
    ("Innovapost Internal Releases Nexus" at nexusBase + "/repository/maven-releases")
      .withAllowInsecureProtocol(true)

  lazy val nexusCreds: FileCredentials = new FileCredentials(
    Path.userHome / ".sbt" / "innovapost-nexus-credentials"
  )

  def publishing(project: Project): Project = {
    project
      .settings(
        // configure Aether deploy to deploy proper Maven-like artifacts
        // overridePublishBothSettings,
        publishTo := {
          if (version.value.endsWith("SNAPSHOT"))
            Some(nexusSnapshots)
          else
            Some(nexusReleases)
        },
        publishMavenStyle := true,
        credentials += nexusCreds,
        ThisBuild / versionScheme := Some("semver-spec"),
        publishConfiguration := publishConfiguration.value
          .withOverwrite(true)
          .withPublishMavenStyle(true)
      )
  }
}
