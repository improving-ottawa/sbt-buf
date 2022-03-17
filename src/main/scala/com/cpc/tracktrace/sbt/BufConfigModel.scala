package com.cpc.tracktrace.sbt

import io.circe.{Encoder, Json}

sealed trait BreakingUseT {
  def label: String
  override def toString: String = label
}
abstract class BreakingUse(val label: String) extends BreakingUseT

object BreakingUse {
  implicit val useEncoder = Encoder.instance[BreakingUse](u => Json.fromString(u.label))
}

case object File extends BreakingUse("FILE")
case object Package extends BreakingUse("PACKAGE")
case object Wire extends BreakingUse("WIRE")
case object WireJson extends BreakingUse("WIRE_JSON")

case class Breaking(use: List[BreakingUse])
object Breaking {
  def defaultBreakingConfig = Breaking(List(File))
  import io.circe.generic.semiauto.deriveEncoder
  implicit val usesEncoder = deriveEncoder[Breaking]
}

sealed trait LintUseT {
  def label: String
  override def toString: String = label
}
abstract class LintUse(val label: String) extends LintUseT

object LintUse {
  implicit val lintUseEncoder = Encoder.instance[LintUse](u => Json.fromString(u.label))
}

case object Minimal extends LintUse("MINIMAL")
case object Basic extends LintUse("BASIC")
case object Default extends LintUse("DEFAULT")
case object Comments extends LintUse("COMMENTS")
case object UnaryRpc extends LintUse("UNARY_RPC")

case class Lint(use: List[LintUse], ignore: List[String] = List.empty[String])
object Lint {
  def defaultLintConfig = Lint(List(Default))
  import io.circe.generic.semiauto.deriveEncoder
  implicit val encoder = deriveEncoder[Lint]
}

case class ModuleConfig(breaking: Breaking, lint: Lint, version: String = "v1")
object ModuleConfig {
  val Default: ModuleConfig = ModuleConfig(
    Breaking.defaultBreakingConfig,
    Lint.defaultLintConfig)

  def defaultWithIgnoreLintDirs(ignores: List[String]): ModuleConfig = {
    Default.copy(lint = Default.lint.copy(ignore = ignores))
  }

  import io.circe.generic.semiauto.deriveEncoder
  implicit val moduleConfigEncoder = deriveEncoder[ModuleConfig]
}

final case class WorkspaceConfig(directories: Seq[String], version: String = "v1")
object WorkspaceConfig {
  import io.circe.generic.semiauto.deriveEncoder
  implicit val workspaceEncoder = deriveEncoder[WorkspaceConfig]
}
