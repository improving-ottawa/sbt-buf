package com.cpc.tracktrace.sbt


import sbt.Keys.*
import sbt.internal.util.ManagedLogger
import sbt.librarymanagement.DependencyResolution
import sbt.plugins.JvmPlugin
import sbt.{Artifact, AutoPlugin, Compile, File, ModuleID, file, settingKey, taskKey, io as sbtIo, *}
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

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
      val bufFetchAgainstTarget = taskKey[File]("Fetches against target image as an artifact, using bufAgainstVersion")
      val bufCompatCheck = inputKey[Unit]("Task that runs the Buf compatibility check.  Accepts the version string to resolve the dependency")

      val breakingCategory = settingKey[String]("Breaking category")
    }
  }

  import autoImport.Buf.*

  private def fetchAgainstTarget(againstArtifactModule: ModuleID, log: ManagedLogger, lm: DependencyResolution, targetDir: File): File = {

    val formatExtension = againstArtifactModule.explicitArtifacts.head.extension
    def downloadArtifact(targetDirectory: File, moduleId: ModuleID): Future[File] = {
      log.info(s"Fetching Buf against target image: ${moduleId}")
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
            files.find(_.getPath.endsWith(s".${formatExtension}")).getOrElse(throw new IllegalStateException(s"Could not find expected Buf image against target from ${moduleId}"))
          })
        }
      }
    }
    def cacheKey(moduleId: ModuleID): String = {
      val classifier = moduleId.explicitArtifacts.headOption.flatMap(_.classifier).getOrElse("")
      s"${moduleId.name}-$classifier-${moduleId.revision}.${formatExtension}"
    }

    IO.createDirectory(targetDir)
    val cache = new protocbridge.FileCache[ModuleID](
      targetDir,
      downloadArtifact,
      cacheKey
    )

    scala.concurrent.Await.result(
      cache.get(againstArtifactModule),
      scala.concurrent.duration.Duration.Inf
    )
  }

  private def runBufCompatCheck(): Def.Initialize[InputTask[Unit]] = {
    import complete.DefaultParsers.*
    val parser = spaceDelimited("<arg>")
    println("what the hell man")
    Def.inputTask {
      val log = streams.value.log
      val againstArtifactVersion = parser.parsed.headOption.getOrElse(throw new IllegalStateException("No Buf against target artifact version provided"))
      log.debug(s"Using $againstArtifactVersion for against artifact version in Buf breaking change detection")
      val lm = (Compile/dependencyResolution).value

      val againstModule = (organization.value %% artifact.value.name % againstArtifactVersion) artifacts bufArtifactDefinition.value
      val outdir = bufAgainstImageDir.value

      val againstImage = fetchAgainstTarget(againstModule, log, lm, outdir)

      generateBufFiles.value
      val currentImage = generateBufImage.value

      import scala.sys.process.*
      log.info(s"Running Buf breaking change detector against ${againstImage.getAbsolutePath}...")
      // TODO:  clean up into a nicer Process
      val result = s"buf breaking --against ${againstImage.getAbsolutePath} ${currentImage.getAbsolutePath}" ! streams.value.log
      if (result != 0) {
        log.error(s"Unexpected exit code from Buf breaking: ${result}")
        throw new IllegalStateException("Buf breaking change detector failed")
      }
    }
  }

  override lazy val projectSettings = Seq(
    bufImageArtifact := true,
    bufArtifactDefinition := Artifact(artifact.value.name, BufImageArtifactType, bufImageExt.value, Some(BufImageArtifactClassifier), Vector.empty, None),
    bufImageDir := (Compile / target).value / "buf",
    bufImageExt := "bin",
    bufAgainstImageDir := (Compile / target).value / "buf-against",
    breakingCategory := "FILE",
    generateBufFiles := {
      (Compile / PB.generate).value
      val srcModuleDirs = (Compile / PB.includePaths).value.map(_.getPath).map(file).filter(d => d.isDirectory && d.list().nonEmpty)
      val cat = breakingCategory.value
      val log = streams.value.log
      import io.circe.syntax.EncoderOps
      import io.circe.yaml.syntax.*
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
      import scala.sys.process.*
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
//    bufFetchAgainstTarget := fetchAgainstTarget(bufCompatCheck.evaluated),
    bufCompatCheck := runBufCompatCheck().evaluated
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
