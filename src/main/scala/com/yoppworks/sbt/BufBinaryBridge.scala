package com.yoppworks.sbt

object BufBinaryBridge {
  private val UNKNOWN = "unknown"
  private val X86_64_RE = "^(x8664|amd64|ia32e|em64t|x64)$".r

  private def normalizedOs(s: String): String =
    normalize(s) match {
      case p if p.startsWith("aix") => "Linux"
      case p if p.startsWith("hpux") => "Linux"
      case p if p.startsWith("linux") => "Linux"
      case p if p.startsWith("osx") || p.startsWith("macosx") => "Darwin"
      case p if p.startsWith("windows") => "Windows"
      case p if p.startsWith("freebsd") => "Linux"
      case p if p.startsWith("openbsd") => "Linux"
      case p if p.startsWith("netbsd") => "Linux"
      case _ => UNKNOWN
    }

  private def normalizedArch(s: String, os: String): String = {
    val r = normalize(s) match {
      case X86_64_RE(_) => "x86_64"
      case "aarch64" if os == "Linux" => "aarch64"
      case "aarch64" => "arm64"
      case _ => UNKNOWN
    }
    if (r != UNKNOWN && os == "Windows") {
      r + ".exe"
    } else {
      r
    }
  }

  def detectedClassifier(): String = {
    val osName = sys.props.getOrElse("os.name", "")
    val osArch = sys.props.getOrElse("os.arch", "")
    val OS = normalizedOs(osName)
    s"$OS-${normalizedArch(osArch, OS)}"
  }

  private def normalize(s: String) =
    s.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+", "")
}
