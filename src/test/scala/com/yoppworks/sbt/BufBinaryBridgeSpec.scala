package com.yoppworks.sbt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.org.webcompere.systemstubs.properties.SystemProperties

class BufBinaryBridgeSpec extends AnyFlatSpec with Matchers {

  behavior of "BufBinaryBridge"

  def testBridgeHardwareResolution(osNameProp: String, archNameProp: String, expectedResolution: String): Unit = {
    new SystemProperties().execute(() => {
      System.setProperty("os.name", osNameProp)
      System.setProperty("os.arch", archNameProp)
      BufBinaryBridge.detectedClassifier()
    }) should be(expectedResolution)
  }

  it should "correctly identify Darwin based system with arm hardware" in {
    testBridgeHardwareResolution(
      "Mac OS X",
      "aarch64",
      "Darwin-arm64"
    )
  }

  it should "correctly identify Darwin based system with x86 hardware" in {
    testBridgeHardwareResolution(
      "Mac OS X",
      "x86_64",
      "Darwin-x86_64"
    )
  }

  it should "correctly identify Linux based system with arm hardware" in {
    testBridgeHardwareResolution(
      "Linux",
      "aarch64",
      "Linux-aarch64"
    )
  }

  it should "correctly identify Linux based system with x86 hardware" in {
    testBridgeHardwareResolution(
      "Linux",
      "x86_64",
      "Linux-x86_64"
    )
  }
}
