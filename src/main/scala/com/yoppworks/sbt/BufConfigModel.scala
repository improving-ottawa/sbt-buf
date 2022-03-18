package com.yoppworks.sbt

import io.circe.Encoder

sealed trait BreakingUse {
  def label: String
  override def toString: String = label
}
object BreakingUse {
  implicit val useEncoder = Encoder.instance[BreakingUse](u => io.circe.Json.fromString(u.label))
}
abstract class AbsBreakingUse(val label: String) extends BreakingUse

case object File     extends AbsBreakingUse("FILE")
case object Package  extends AbsBreakingUse("PACKAGE")
case object Wire     extends AbsBreakingUse("WIRE")
case object WireJson extends AbsBreakingUse("WIRE_JSON")

case class Breaking(use: Seq[BreakingUse])
object Breaking {
  def defaultBreakingConfig = Breaking(Seq(File))
  import io.circe.generic.semiauto.deriveEncoder
  implicit val usesEncoder = deriveEncoder[Breaking]
}

sealed trait LintUse {
  def label: String
  override def toString: String = label
}
abstract class AbsLintUse(val label: String) extends LintUse

object AbsLintUse {
  implicit val lintUseEncoder = Encoder.instance[AbsLintUse](u => io.circe.Json.fromString(u.label))
}

case object Minimal  extends AbsLintUse("MINIMAL")
case object Basic    extends AbsLintUse("BASIC")
case object Default  extends AbsLintUse("DEFAULT")
case object Comments extends AbsLintUse("COMMENTS")
case object UnaryRpc extends AbsLintUse("UNARY_RPC")

case class Lint(use: Seq[AbsLintUse], ignore: Seq[String] = Seq.empty[String]) {
  def withIgnores(ignores: Seq[String]) = copy(ignore = ignores)
}
object Lint {
  def defaultLintConfig = Lint(Seq(Default))
  import io.circe.generic.semiauto.deriveEncoder
  implicit val encoder = deriveEncoder[Lint]
}

case class ModuleConfig(breaking: Breaking, lint: Lint, version: String = "v1")
object ModuleConfig {
  def apply(breakingUses: Seq[BreakingUse], lintIgnores: Seq[String]): ModuleConfig =
    ModuleConfig(Breaking(breakingUses), Lint.defaultLintConfig.withIgnores(lintIgnores))
  import io.circe.generic.semiauto.deriveEncoder
  implicit val moduleConfigEncoder = deriveEncoder[ModuleConfig]
}

final case class WorkspaceConfig(directories: Seq[String], version: String = "v1")
object WorkspaceConfig {
  import io.circe.generic.semiauto.deriveEncoder
  implicit val workspaceEncoder = deriveEncoder[WorkspaceConfig]
}

sealed trait ImageExtensionT {
  def ext: String
  override def toString: String = ext
}
abstract class ImageExtension(val ext: String) extends ImageExtensionT

case object Binary extends ImageExtension("bin")
case object Json   extends ImageExtension("json")
