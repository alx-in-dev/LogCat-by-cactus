package com.cactus.logcactus.standalone

import com.android.tools.idea.adb.AdbFileProvider
import java.util.concurrent.TimeUnit

/** Подмена `AdbShell` основного приложения: adb берётся у Студии, а не из зашитой копии. Нужна общему `DeviceInfoCollector`. */
object AdbShell {
  private const val TIMEOUT_SEC = 4L

  fun run(serial: String, command: String): String? = runCatching {
    val adb = AdbFileProvider.fromApplication()?.get() ?: return null
    val process = ProcessBuilder(adb.path, "-s", serial, "shell", command).redirectErrorStream(true).start()
    if (!process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      null
    } else {
      process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
  }.getOrNull()
}
