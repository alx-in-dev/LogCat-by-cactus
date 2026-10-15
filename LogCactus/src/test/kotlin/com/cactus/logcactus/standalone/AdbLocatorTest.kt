package com.cactus.logcactus.standalone

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdbLocatorTest {
  // Друг без Android SDK: adb должен извлечься из ресурсов в ~/.logcactus/bin и реально запускаться.
  @Test
  fun bundledAdbIsExtractedAndRuns() {
    val home = Files.createTempDirectory("logcactus-home")
    System.setProperty("user.home", home.toString())

    val path = AdbLocator.path()
    assertTrue(path.startsWith(home.toString()), "adb должен быть извлечён в user.home, а не найден в системе: $path")
    assertTrue(File(path).exists(), "нет файла $path")

    val out = ProcessBuilder(path, "version").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
    assertTrue(out.contains("Android Debug Bridge"), "adb version вернул: $out")
  }
}
