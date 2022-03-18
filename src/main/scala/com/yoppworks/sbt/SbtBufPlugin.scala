package com.yoppworks.sbt

import sbt.Keys.*
import sbt.internal.util.ManagedLogger
import sbt.librarymanagement.DependencyResolution
import sbt.plugins.JvmPlugin
import sbt.{
  Artifact,
  AutoPlugin,
  Compile,
  File,
  ModuleID,
  file,
  settingKey,
  taskKey,
  io as sbtIo,
  *
}
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}

object SbtBufPlugin extends AutoPlugin {

  override def trigger  = allRequirements
  override def requires = JvmPlugin && ProtocPlugin

  object autoImport {
    object Buf {
      val BufImageArtifactType       = "buf-image"
      val BufImageArtifactClassifier = "buf"
      // create buf image, publish buf image
      val generateBufImage =
        taskKey[File]("Generate buf image from proto definitions in this project")
      val addImageArtifactToBuild = settingKey[Boolean](
        "Whether the generated buf image should be added to the project as an artifact.  Will have the effect of publishing the artifact with publish or publishLocal tasks."
      )
      val artifactDefinition = settingKey[Artifact]("Artifact definition for bug image artifact")
      val imageDir           = settingKey[File]("Target directory in which Buf image is generated")
      val imageExt =
        settingKey[ImageExtension]("Format for Buf generate and published artifacts")
      val generateBufFiles =
        taskKey[Unit]("Generate Buf files in each of the 'modules' managed by ScalaPB")

      // against
      val againstImageDir =
        settingKey[File]("Target directory in which Buf against target image is downloaded to")
      val bufFetchAgainstTarget =
        taskKey[File]("Fetches against target image as an artifact, using bufAgainstVersion")
      val bufCompatCheck = inputKey[Unit](
        "Task that runs the Buf compatibility check.  Accepts the version string to resolve the dependency"
      )

      val bufLint = inputKey[Unit](
        "Run buf lint command against current working directory or a specified published image artifact version"
      )

      val breakingCategory = settingKey[Seq[BreakingUse]]("Breaking category")
    }
  }

  import autoImport.Buf.*

  private def fetchAgainstTarget(
      againstArtifactModule: ModuleID,
      log: ManagedLogger,
      lm: DependencyResolution,
      targetDir: File
  ): File = {
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
          ).fold(
            { w =>
              throw w.resolveException
            },
            { files =>
              // is this sufficient? better test a dependent module
              files
                .find(_.getPath.endsWith(s".${formatExtension}"))
                .getOrElse(
                  throw new IllegalStateException(
                    s"Could not find expected Buf image against target from ${moduleId}"
                  )
                )
            }
          )
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
    Def.inputTask {
      val log = streams.value.log
      val againstArtifactVersion = parser.parsed.headOption.getOrElse(
        throw new IllegalStateException("No Buf against target artifact version provided")
      )
      log.debug(
        s"Using $againstArtifactVersion for against artifact version in Buf breaking change detection"
      )
      val lm = (Compile / dependencyResolution).value

      val againstModule =
        (organization.value %% artifact.value.name % againstArtifactVersion) artifacts artifactDefinition.value
      val outdir = againstImageDir.value / "compat"

      val againstImage = fetchAgainstTarget(againstModule, log, lm, outdir)

      val currentImage = generateBufImage.value

      import scala.sys.process.*
      log.info(s"Running Buf breaking change detector against ${againstImage.getAbsolutePath}...")
      val result = Process(
        Seq(
          "buf",
          "breaking",
          "--against",
          againstImage.getAbsolutePath,
          currentImage.getAbsolutePath
        )
      ) ! streams.value.log

      if (result != 0) {
        throw new IllegalStateException(
          s"Buf breaking change detector failed with exit code: $result"
        )
      } else {
        log.info("Buf breaking change detection passed successfully!")
      }
    }
  }

  private def runBufLint(): Def.Initialize[InputTask[Unit]] = {
    import complete.DefaultParsers.*
    val parser = spaceDelimited("<arg>")
    Def.inputTask {
      val log = streams.value.log
      import scala.sys.process.*
      log.info(s"Running Buf lint against working directory...")
      val result = Process(
        Seq(
          "buf",
          "lint",
          "./"
        )
      ) ! streams.value.log

      if (result != 0) {
        throw new IllegalStateException(s"Buf lint command failed with exit code ${result}")
      } else {
        log.info("Buf lint passed successfully!")
      }
    }
  }
  override lazy val projectSettings = Seq(
    addImageArtifactToBuild := true,
    artifactDefinition := Artifact(
      artifact.value.name,
      BufImageArtifactType,
      imageExt.value.ext,
      Some(BufImageArtifactClassifier),
      Vector.empty,
      None
    ),
    imageDir         := (Compile / target).value / "buf",
    imageExt         := Binary,
    againstImageDir  := (Compile / target).value / "buf-against",
    breakingCategory := Seq(File),
    generateBufFiles := {
      (Compile / PB.generate).value
      val srcModuleDirs = (Compile / PB.includePaths).value
        .map(_.getPath)
        .map(file)
        .filter(d => d.isDirectory && d.list().nonEmpty)
      val log = streams.value.log
      import io.circe.syntax.EncoderOps
      import io.circe.yaml.syntax.*
      // ignore all imports during linting, so collect all root folders within all external sources
      val importProtosToIgnore = Seq(
        (Compile / PB.externalIncludePath).value,
        (Compile / PB.externalSourcePath).value
      ).filter(_.isDirectory).flatMap(_.listFiles()).filter(_ != null).map(_.getName)
      srcModuleDirs.map(_ / "buf.yaml").foreach { moduleFile =>
        log.info(s"Writing buf module file to ${moduleFile.getAbsolutePath}")
        IO.write(
          moduleFile,
          ModuleConfig(breakingCategory.value, importProtosToIgnore).asJson.asYaml.spaces2.getBytes
        )
      }
      val bufWorkspaceFile = baseDirectory.value / "buf.work.yaml"
      val relativeSrcDirs = srcModuleDirs.map(
        _.relativeTo(baseDirectory.value).getOrElse(
          throw new IllegalStateException("Buf src dir must be relative to project root")
        )
      )
      log.debug(s"Writing buf workspace file to ${bufWorkspaceFile.getAbsolutePath}")
      IO.write(
        bufWorkspaceFile,
        WorkspaceConfig(relativeSrcDirs.map(_.getPath)).asJson.asYaml.spaces2.getBytes
      )
    },
    generateBufImage := {
      generateBufFiles.value
      val imageDirFile = imageDir.value
      if (!imageDirFile.exists()) {
        IO.createDirectory(imageDirFile)
      }
      val image: File = imageDirFile / s"buf-workingdir-image.${imageExt.value}"

      val log = streams.value.log
      import scala.sys.process.*
      log.info(s"Building Buf image to ${image.getAbsolutePath}...")
      val projectDir = baseDirectory.value
      val result = Process(
        Seq(
          "buf",
          "build",
          projectDir.getAbsolutePath,
          "-o",
          image.getAbsolutePath
        )
      ) ! streams.value.log
      if (result != 0) {
        log.error(s"Unexpected exit code from Buf build: ${result}")
        throw new IllegalStateException("Buf build failed")
      }
      image
    },
    packagedArtifacts := {
      if (addImageArtifactToBuild.value)
        packagedArtifacts.value.updated(artifactDefinition.value, generateBufImage.value)
      else packagedArtifacts.value
    },
    artifacts := {
      if (addImageArtifactToBuild.value)
        artifacts.value :+ artifactDefinition.value
      else artifacts.value
    },
    bufCompatCheck := runBufCompatCheck().evaluated,
    bufLint        := runBufLint().evaluated
  )
}
