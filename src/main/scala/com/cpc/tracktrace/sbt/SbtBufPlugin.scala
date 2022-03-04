package com.cpc.tracktrace.sbt


import sbt.Keys.*
import sbt.plugins.JvmPlugin
import sbt.{Artifact, AutoPlugin, Compile, File, ModuleID, SettingsDefinition, file, settingKey, taskKey, io as sbtIo, *}
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}

object SbtBufPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin && ProtocPlugin

  object autoImport {
    object Buf {
      val BufImageArtifactType = "buf-image"
      val BufImageArtifactClassifier = "buf"
      // create buf image, publish buf image
      val generateBufImage = taskKey[File]("Generate buf image from proto definitions in this project")
      val bufImageArtifact = settingKey[Boolean]("Whether the generated buf image should be added to the project as an artifact.  Will have the effect of publishing the artifact with publish or publishLocal tasks.")
      val bufArtifactDefinition = settingKey[Artifact]("Artifact definition for bug image artifact")
      val bufImageDir = settingKey[File]("Target directory in which Buf image is generated")
      val bufImageExt = settingKey[String]("Format for Buf generate and published artifacts")
      val generateBufFiles = taskKey[Unit]("Generate Buf files in each of the 'modules' managed by ScalaPB")

      // against
      val bufAgainstImageDir = settingKey[File]("Target directory in which Buf against target image is downloaded to")
      val bufAgainstImage = settingKey[File]("Location of the Buf image to use as the against target in compatibility checks")
      val bufAgainstVersion = settingKey[String]("Version of Buf image to resolve for against target")
      val bufAgainstDependency = settingKey[ModuleID]("Dependency to resolve the Buf image for the against target")
      val bufFetchAgainstTarget = taskKey[File]("Fetches against target image as an aritfact, using bufAgainstVersion")
      val bufCompatCheck = taskKey[Unit]("Task that runs the Buf compatibility check.  Accepts the version string to resolve the dependency")

      val breakingCategory = settingKey[String]("Breaking category")
    }
  }

  import autoImport.Buf.*

  import io.circe.generic.semiauto.deriveEncoder
  final case class Use(use: Seq[String])
  implicit val useEncoder = deriveEncoder[Use]
  final case class ModuleConfig(breaking: Use, version: String = "v1")
  implicit val moduleConfigEncoder = deriveEncoder[ModuleConfig]
  object ModuleConfig {
    def apply(breakingCategory: String): ModuleConfig = ModuleConfig(Use(List(breakingCategory)))
  }
  final case class WorkspaceConfig(directories: Seq[String], version: String = "v1")
  implicit val workspaceEncoder = deriveEncoder[WorkspaceConfig]

  lazy val bufBreakingPlugin = PB.gens.plugin(
    "buf_breaking",
    path="/usr/local/bin/protoc-gen-buf-breaking"
  )

  override lazy val projectSettings = Seq(
    bufImageArtifact := true,
    bufArtifactDefinition := Artifact(artifact.value.name, BufImageArtifactType, bufImageExt.value, Some(BufImageArtifactClassifier), Vector.empty, None),

    bufImageDir := (Compile / target).value / "buf",
    bufImageExt := "bin",
    bufAgainstVersion := "invalid",
    bufAgainstDependency := (organization.value %% artifact.value.name % bufAgainstVersion.value) artifacts bufArtifactDefinition.value,
    bufAgainstImageDir := (Compile / target).value / "buf-against",
    breakingCategory := "FILE",
    generateBufFiles := {
      (Compile / PB.generate).value
      val srcModuleDirs = (Compile / PB.includePaths).value.map(_.getPath).map(file).filter(d => d.isDirectory && d.list().nonEmpty)
      val cat = breakingCategory.value
      val log = streams.value.log
      import io.circe.syntax.EncoderOps, io.circe.yaml.syntax._
      srcModuleDirs.map(_ / "buf.yaml").foreach {
        case bufModFile if !bufModFile.exists() =>
          log.info(s"Writing buf module file to ${bufModFile.getAbsolutePath}")
          IO.write(
            bufModFile,
            ModuleConfig(cat).asJson.asYaml.spaces2.getBytes
          )
        case _ =>
          log.debug("Buf module file already exists")
      }
      val bufWorkspaceFile = baseDirectory.value / "buf.work.yaml"
      val relativeSrcDirs = srcModuleDirs.map(_.relativeTo(baseDirectory.value).getOrElse(throw new IllegalStateException("Buf src dir must be relative to project root")))
      if (!bufWorkspaceFile.exists()) {
        log.debug(s"Writing buf workspace file to ${bufWorkspaceFile.getAbsolutePath}")
        IO.write(
          bufWorkspaceFile,
          WorkspaceConfig(relativeSrcDirs.map(_.getPath)).asJson.asYaml.spaces2.getBytes
        )
      }
    },
    generateBufImage := {
      generateBufFiles.value
      val imageDirFile = bufImageDir.value
      if (!imageDirFile.exists()) {
        IO.createDirectory(imageDirFile)
      }
      val image: File = imageDirFile / s"buf-image.${bufImageExt.value}"

      val log = streams.value.log
      import scala.sys.process._
      log.info(s"Building Buf image to ${image.getAbsolutePath}...")
      // TODO:  clean up into a nicer Process
      val result = s"buf build ./ -o ${image.getAbsolutePath}" ! streams.value.log
      if (result != 0) {
        log.error(s"Unexpected exit code from Buf build: ${result}")
        throw new IllegalStateException("Buf build failed")
      }
      image
    },
    packagedArtifacts := {
      if (bufImageArtifact.value)
        packagedArtifacts.value.updated(bufArtifactDefinition.value, generateBufImage.value)
      else packagedArtifacts.value
    },
    artifacts := {
      if (bufImageArtifact.value)
        artifacts.value :+ bufArtifactDefinition.value
      else artifacts.value
    },

    // fetch image tasks
//    bufCompatCheck := {
//      import complete.DefaultParsers._
//      val version = spaceDelimited("<arg>").parsed.head
//      bufAgainstVersion := version
//      bufFetchAgainstTarget.value
//
//    },
    bufFetchAgainstTarget := {
      val log = streams.value.log

      val lm  = (Compile / dependencyResolution).value

      def downloadArtifact(targetDirectory: File, moduleId: ModuleID): Future[File] = {
        log.info("Fetching Buf against target image")
        Future {
          blocking {
            lm.retrieve(
              moduleId,
              None,
              targetDirectory,
              log
            ).fold({ w =>
              throw w.resolveException }, { files =>
              // is this sufficient? better test a dependent module
              files.find(_.getPath.endsWith(s".${bufImageExt.value}")).getOrElse(throw new IllegalStateException(s"Could not find expected Buf image against target from ${moduleId}"))
            })
          }
        }
      }
      def cacheKey(moduleId: ModuleID): String = {
        val classifier = moduleId.explicitArtifacts.headOption.flatMap(_.classifier).getOrElse("")
        s"${moduleId.name}-$classifier-${moduleId.revision}.${bufImageExt.value}"
      }

      val outdir = bufAgainstImageDir.value
      IO.createDirectory(outdir)
      val cache = new protocbridge.FileCache[ModuleID](
        outdir,
        downloadArtifact,
        cacheKey
      )

      scala.concurrent.Await.result(
        cache.get(bufAgainstDependency.value),
        scala.concurrent.duration.Duration.Inf
      )
    },
    bufCompatCheck := {
      generateBufFiles.value
      val bufAgainstTarget = bufFetchAgainstTarget.value
      val currentImage = generateBufImage.value

      val log = streams.value.log
      import scala.sys.process._
      log.info(s"Running Buf breaking change detector against ${bufAgainstTarget.getAbsolutePath}...")
      // TODO:  clean up into a nicer Process
      val result = s"buf breaking --against ${bufAgainstTarget.getAbsolutePath} ${currentImage.getAbsolutePath}" ! streams.value.log
      if (result != 0) {
        log.error(s"Unexpected exit code from Buf breaking: ${result}")
        throw new IllegalStateException("Buf breaking change detector failed")
      }
    },
    //PB.generate := PB.generate.dependsOn(bufFetchAgainstTarget).value,
//    Compile / PB.targets := {
//      val bufParams = """{"against_input": "target/", "limit_to_input_files": true, "log_level": "debug"}"""
//      Seq(
//        (bufBreakingPlugin, Seq(s"${bufParams}")) -> (ThisBuild/ baseDirectory).value / "buf-output",
//      )
//    }
  )

  override lazy val buildSettings = Seq()

  override lazy val globalSettings = Seq()
}
