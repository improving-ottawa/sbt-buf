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
      val bufSrcDirs = taskKey[Seq[File]]("All source directories for a given module")
      val hasBufSrcs = taskKey[Boolean](
        "Checks whether a given module has proto sources relevant to Buf operations"
      )
      val generateBufImage =
        taskKey[Option[File]]("Generate buf image from proto definitions in this project")
      val addImageArtifactToBuild = settingKey[Boolean](
        "Whether the generated buf image should be added to the project as an artifact.  Will have the effect of publishing the artifact with publish or publishLocal tasks."
      )
      val bufSrcModuleFile =
        taskKey[Option[File]]("Buf Module file for the primary source of this (sbt) module")
      val artifactDefinition = settingKey[Artifact]("Artifact definition for bug image artifact")
      val imageDir           = settingKey[File]("Target directory in which Buf image is generated")
      val imageExt =
        settingKey[ImageExtension]("Format for Buf generate and published artifacts")
      val generateBufFiles =
        taskKey[Seq[File]]("Generate Buf files in each of the 'modules' managed by ScalaPB")

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
    imageExt         := ImageExtension.Binary,
    againstImageDir  := (Compile / target).value / "buf-against",
    breakingCategory := Seq(BreakingUse.File),
    bufSrcDirs := {
      (Compile / PB.generate).value
      val module     = target.value
      val targetDirs = module.listFiles()
      (Compile / PB.includePaths).value
        .map(_.getPath)
        .map(file)
        .filter(f => f.isDirectory && f.list().nonEmpty)
        // TODO:  clean this up - last filter is too cryptic
        // filter out external imports from sibling module dependencies,
        // which are a duplication of the external imports brought in by the current module anyhow
        .filterNot(d => d.getName.contains("protobuf_external") && !targetDirs.contains(d))
    },
    hasBufSrcs := {
      import scala.reflect.io.Directory
      val baseSrc = sourceDirectory.value
      bufSrcDirs.value.exists(f =>
        f.relativeTo(baseSrc).isDefined && new Directory(f).deepFiles.nonEmpty
      )
    },
    bufSrcModuleFile := {
      val srcDirs = (Compile / PB.protoSources).value
      bufSrcDirs.value
        .map(s => (s \ "buf.yaml").get().headOption)
        .collectFirst {
          case Some(bufMod) if srcDirs.exists(sd => bufMod.relativeTo(sd).nonEmpty) => bufMod
        }
    },
    generateBufFiles := {
      val log           = streams.value.log
      val srcModuleDirs = bufSrcDirs.value
      val projectBase   = baseDirectory.value
      log.debug(s"Found these Buf src directories for module at ${projectBase}: ${srcModuleDirs}")
      if (!hasBufSrcs.value) {
        log.warn(
          s"Module at ${projectBase} does not seem to contain and proto sources, skipping any Buf artifact generation"
        )
        Seq.empty
      } else {
        import io.circe.syntax.EncoderOps
        import io.circe.yaml.syntax.*
        // ignore all imports during linting, so collect all root folders within all external sources
        val importProtosToIgnore = Seq(
          (Compile / PB.externalIncludePath).value,
          (Compile / PB.externalSourcePath).value
        ).filter(_.isDirectory).flatMap(_.listFiles()).filter(_ != null).map(_.getName)
        val moduleFiles = srcModuleDirs.map(_ / "buf.yaml").map { moduleFile =>
          log.info(s"Writing buf module file to ${moduleFile.getAbsolutePath}")
          IO.write(
            moduleFile,
            ModuleConfig(
              breakingCategory.value,
              importProtosToIgnore
            ).asJson.asYaml.spaces2.getBytes
          )
          moduleFile
        }
        val projectBase      = baseDirectory.value
        val bufWorkspaceFile = projectBase / "buf.work.yaml"
        val moduleTarget     = target.value
        val relativeSrcDirs =
          srcModuleDirs
            .map(srcDir => srcDir -> srcDir.relativeTo(projectBase))
            .map {
              case (_, Some(rd))  => rd
              case (srcDir, None) =>
                // for cases of cross-module proto dependency (non-relative), we must copy the dependency to be 'local' (relative)
                // to this sbt module to abide by Buf's requirement that all module/workspace dependencies be relative
                log.info(
                  s"Found non relative src directory ${srcDir}, copying to ${moduleTarget} to appear relative to Buf workspace"
                )
                val rd = copyRelativeDependency(moduleTarget, srcDir)
                rd.relativeTo(projectBase).getOrElse {
                  throw new IllegalStateException(
                    s"Copied source directory ${rd} is not relative to project base ${projectBase}"
                  )
                }
            }
            .distinct
        log.info(s"Writing buf workspace file to ${bufWorkspaceFile.getAbsolutePath}")
        IO.write(
          bufWorkspaceFile,
          WorkspaceConfig(relativeSrcDirs.map(_.getPath)).asJson.asYaml.spaces2.getBytes
        )
        moduleFiles :+ bufWorkspaceFile
      }
    },
    generateBufImage := {
      val log      = streams.value.log
      val bufFiles = generateBufFiles.value
      if (bufFiles.isEmpty) {
        log.debug(s"No Buf files generated, skipping Buf image generation")
        None
      } else {
        val imageDirFile = imageDir.value
        if (!imageDirFile.exists()) {
          IO.createDirectory(imageDirFile)
        }
        val image: File = imageDirFile / s"buf-workingdir-image.${imageExt.value}"

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
        ) ! log
        if (result != 0) {
          log.error(s"Unexpected exit code from Buf build: ${result}")
          throw new IllegalStateException("Buf build failed")
        }
        Some(image)
      }
    },
    packagedArtifacts := {
      if (addImageArtifactToBuild.value && hasBufSrcs.value)
        packagedArtifacts.value.updated(
          artifactDefinition.value,
          generateBufImage.value.getOrElse(
            throw new IllegalStateException("Buf sources exist but no image was generated")
          )
        )
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
      val module            = target.value
      val maybeCurrentImage = generateBufImage.value
      val log               = streams.value.log
      maybeCurrentImage match {
        case None =>
          log.warn(
            s"No sources/image found for current module ${module}, skipping compat check for this module"
          )
        case Some(currentImage) =>
          val againstArtifactVersion = parser.parsed.headOption.getOrElse(
            throw new IllegalStateException("No Buf against target artifact version provided")
          )
          log.info(
            s"Using $againstArtifactVersion for against artifact version in Buf breaking change detection"
          )

          val configModule = bufSrcModuleFile.value.getOrElse(
            throw new IllegalStateException(
              "Cannot determine module proto src Buf module to use for input configuration of compatibility check"
            )
          )

          val lm = (Compile / dependencyResolution).value

          val againstModule =
            (organization.value %% artifact.value.name % againstArtifactVersion) artifacts artifactDefinition.value
          val outdir = againstImageDir.value / "compat"

          val againstImage = fetchAgainstTarget(againstModule, log, lm, outdir)

          import scala.sys.process.*
          log.info(
            s"Running Buf breaking change detector against ${againstImage.getAbsolutePath}..."
          )
          val result = Process(
            Seq(
              "buf",
              "breaking",
              "--against",
              againstImage.getAbsolutePath,
              currentImage.getAbsolutePath,
              "--config",
              configModule.getAbsolutePath
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
  }

  private def runBufLint(): Def.Initialize[InputTask[Unit]] = {
    import complete.DefaultParsers.*
    val parser = spaceDelimited("<arg>")
    Def.inputTask {
      val log = streams.value.log
      import scala.sys.process.*
      if (!hasBufSrcs.value) {
        ()
      } else {
        log.info(s"Running Buf lint against working directory...")
        val dir = baseDirectory.value
        val result = Process(
          Seq(
            "buf",
            "lint",
            dir.getAbsolutePath
          )
        ) ! streams.value.log

        if (result != 0) {
          throw new IllegalStateException(s"Buf lint command failed with exit code ${result}")
        } else {
          log.info("Buf lint passed successfully!")
        }
      }
    }
  }

  // used for purposes of 'shifting' a cross-module protobuf dependency into a directory relative to the
  // Buf module that depends upon it.  This is to address the difference between the ScalaPB dependency module (allows dependency
  // references between directories that are not relative (within) to the module root) and Buf (requires all module/workspace directory references
  // to be relative to the root)
  private def copyRelativeDependency(moduleTarget: File, depSourceDir: File): File = {
    val bufModulesDepsDir = moduleTarget / "bufModuleDeps"
    if (!bufModulesDepsDir.exists()) {
      IO.createDirectory(bufModulesDepsDir)
    }
    val depTargetDir = bufModulesDepsDir / depSourceDir.getName
    if (!depTargetDir.exists()) {
      IO.createDirectory(depTargetDir)
    }
    IO.copyDirectory(depSourceDir, depTargetDir)
    depTargetDir
  }
}
