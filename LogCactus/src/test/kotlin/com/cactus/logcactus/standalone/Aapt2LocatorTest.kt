package com.cactus.logcactus.standalone

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class Aapt2LocatorTest {
  @Test
  fun bundledAapt2IsExtractedAndRuns() {
    val path = Aapt2Locator.bundled()
    assertNotNull(path, "aapt2 must be bundled for macOS and Windows")
    val p = ProcessBuilder(path.toString(), "version").redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    assertEquals(0, p.waitFor(), out)
    assert(out.contains("Android Asset Packaging Tool")) { out }
  }
}
