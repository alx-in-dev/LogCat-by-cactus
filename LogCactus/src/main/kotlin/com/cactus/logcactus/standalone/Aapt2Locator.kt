package com.cactus.logcactus.standalone

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * aapt2 нужен для названий и иконок приложений (`app:`). Как и adb, зашит в ресурсы (macOS — universal Mach-O из
 * build-tools 37.0.0, Windows — `aapt2.exe` из артефакта `com.android.tools.build:aapt2`, лицензия Apache 2.0, см.
 * `aapt2-NOTICE.txt`) и при первом обращении извлекается в `~/.logcactus/bin`, так что Android SDK у пользователя не нужен.
 */
object Aapt2Locator {
  private val windows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

  /** Путь к извлечённому aapt2; null — для этой ОС бинарь не зашит или извлечь не удалось. */
  fun bundled(): Path? {
    if (!windows && !System.getProperty("os.name", "").contains("Mac", ignoreCase = true)) return null
    val name = if (windows) "aapt2.exe" else "aapt2"
    val target = Paths.get(System.getProperty("user.home"), ".logcactus", "bin", name)
    if (runCatching { Files.isExecutable(target) && Files.size(target) > 0 }.getOrDefault(false)) return target
    return try {
      Files.createDirectories(target.parent)
      val resource = (if (windows) "logcactus/bin/win/" else "logcactus/bin/") + name
      val stream = javaClass.classLoader.getResourceAsStream(resource) ?: return null
      // Через временный файл: прерванное копирование не оставит «готовый» обрезанный бинарь.
      val tmp = target.resolveSibling("$name.tmp")
      stream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
      tmp.toFile().setExecutable(true, false)
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
      target
    } catch (_: Exception) {
      null
    }
  }
}
