package com.cactus.logcactus.standalone

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Резолвит путь к adb. Просто "adb" (PATH lookup) работает из терминала и
 * `./gradlew run` (наследуют шелл-PATH с ~/.zshrc export'ами), но НЕ работает
 * в упакованном .app: Finder/double-click запускает процесс с минимальным
 * системным PATH (`/usr/bin:/bin:/usr/sbin:/sbin`), куда Android SDK и
 * Homebrew не входят — `adb devices` в Terminal видит устройство, а
 * собранный .dmg показывает "No Devices" (баг, замеченный пользователем на
 * собранном дистрибутиве).
 *
 * Чтобы приложение вообще не зависело от того, установлен ли Android SDK у
 * того, кому передали .dmg (пользователь прямо попросил такую независимость),
 * в ресурсы (`src/main/resources/logcactus/bin/adb`) зашит сам бинарь adb —
 * universal Mach-O (arm64+x86_64 одним файлом, ~18МБ), взят из
 * `platform-tools`, лицензия Apache 2.0 (см. `adb-NOTICE.txt` рядом,
 * сохранён как есть для соответствия лицензии). При первом обращении
 * извлекается в `~/.logcactus/bin/adb` (кэшируется, не перезаписывается
 * каждый раз) и используется в приоритете НАД поиском по системе — так
 * работает одинаково что у разработчика с полным Android SDK, что у друга
 * без единого намёка на Android-тулинг.
 */
object AdbLocator {
  private val resolved: String by lazy { locate() }

  fun path(): String = resolved

  private fun locate(): String {
    // Явный override всегда в приоритете — для тех, кто хочет конкретную
    // версию adb (например, отличную от зашитой в ресурсы).
    System.getProperty("logcactus.adb.path")?.takeIf { it.isNotBlank() }?.let { return it }

    extractBundledAdb()?.let { return it }

    val home = System.getProperty("user.home")
    val candidates = listOfNotNull(
      System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
      System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
      System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk\\platform-tools\\adb.exe" },
      "$home/Library/Android/sdk/platform-tools/adb",
      "/opt/homebrew/bin/adb",
      "/usr/local/bin/adb"
    )
    for (candidate in candidates) {
      if (runCatching { Files.isExecutable(Paths.get(candidate)) }.getOrDefault(false)) return candidate
    }
    return "adb" // последний фолбэк на PATH — сработает хотя бы из терминала/gradlew run
  }

  private fun isWindows(): Boolean = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

  private fun extractBundledAdb(): String? {
    // Бандлим adb под macOS (universal Mach-O) и Windows (adb.exe + 2 dll из platform-tools);
    // под Linux дистрибутив пока не собираем.
    val windows = isWindows()
    if (!windows && !System.getProperty("os.name", "").contains("Mac", ignoreCase = true)) return null

    val targetDir: Path = Paths.get(System.getProperty("user.home"), ".logcactus", "bin")
    val exeName = if (windows) "adb.exe" else "adb"
    val target = targetDir.resolve(exeName)
    if (runCatching { Files.isExecutable(target) && Files.size(target) > 0 }.getOrDefault(false)) {
      return target.toString()
    }

    return try {
      Files.createDirectories(targetDir)
      // adb.exe без AdbWinApi.dll/AdbWinUsbApi.dll рядом не запустится — кладём все три; exe последним,
      // чтобы проверка выше не сочла установку готовой при недокопированных dll.
      val resources = if (windows) listOf("AdbWinApi.dll", "AdbWinUsbApi.dll", "adb.exe") else listOf("adb")
      for (name in resources) {
        val dir = if (windows) "logcactus/bin/win/" else "logcactus/bin/"
        val stream = javaClass.classLoader.getResourceAsStream(dir + name) ?: return null
        stream.use { input -> Files.copy(input, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
      }
      target.toFile().setExecutable(true, false)
      target.toString()
    } catch (_: Exception) {
      null
    }
  }
}
