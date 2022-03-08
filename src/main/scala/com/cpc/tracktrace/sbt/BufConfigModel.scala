package com.cpc.tracktrace.sbt

import io.circe.generic.semiauto.deriveEncoder

final case class Use(use: Seq[String])
object Use {
  implicit val useEncoder = deriveEncoder[Use]
}

final case class ModuleConfig(breaking: Use, version: String = "v1")
object ModuleConfig {
  def apply(breakingCategory: String): ModuleConfig = ModuleConfig(Use(List(breakingCategory)))
  implicit val moduleConfigEncoder = deriveEncoder[ModuleConfig]
}

final case class WorkspaceConfig(directories: Seq[String], version: String = "v1")
object WorkspaceConfig {
  implicit val workspaceEncoder = deriveEncoder[WorkspaceConfig]
}
