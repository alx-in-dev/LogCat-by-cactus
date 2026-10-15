package com.cactus.logcactus.standalone

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ActionResult(val ok: Boolean, val message: String)

data class AppDetails(
  val packageName: String,
  val versionName: String,
  val versionCode: String,
  val minSdk: String,
  val targetSdk: String,
  val debuggable: Boolean,
  val installer: String,
  val firstInstall: String,
  val lastUpdate: String
) {
  fun toText(): String = buildString {
    append(packageName).append('\n')
    append("Version:      ").append(versionName.ifBlank { "?" }).append(" (").append(versionCode.ifBlank { "?" }).append(")\n")
    append("SDK:          min ").append(minSdk.ifBlank { "?" }).append(", target ").append(targetSdk.ifBlank { "?" }).append('\n')
    append("Debuggable:   ").append(if (debuggable) "yes" else "no").append('\n')
    if (installer.isNotBlank()) append("Installer:    ").append(installer).append('\n')
    if (firstInstall.isNotBlank()) append("First install: ").append(firstInstall).append('\n')
    if (lastUpdate.isNotBlank()) append("Last update:  ").append(lastUpdate).append('\n')
  }
}

object AppDetailsParser {
  private val VERSION_CODE = Regex("""versionCode=(\d+)""")
  private val VERSION_NAME = Regex("""versionName=(\S+)""")
  private val MIN_SDK = Regex("""minSdk=(\d+)""")
  private val TARGET_SDK = Regex("""targetSdk=(\d+)""")
  private val FLAGS = Regex("""(?m)^\s*flags=\[([^\]]*)]""")
  private val INSTALLER = Regex("""installerPackageName=(\S+)""")
  private val FIRST = Regex("""firstInstallTime=(.+)""")
  private val LAST = Regex("""lastUpdateTime=(.+)""")

  fun parse(pkg: String, dump: String): AppDetails? {
    if (VERSION_NAME.find(dump) == null && VERSION_CODE.find(dump) == null) return null
    fun g(r: Regex) = r.find(dump)?.groupValues?.get(1)?.trim().orEmpty()
    return AppDetails(
      pkg, g(VERSION_NAME), g(VERSION_CODE), g(MIN_SDK), g(TARGET_SDK),
      debuggable = FLAGS.find(dump)?.groupValues?.get(1)?.split(' ')?.contains("DEBUGGABLE") == true,
      installer = g(INSTALLER).takeIf { it != "null" }.orEmpty(),
      firstInstall = g(FIRST),
      lastUpdate = g(LAST)
    )
  }
}

/** Действия над приложением на устройстве через adb, без root. Пакет и URI проверяются перед подстановкой в shell. */
class DeviceActions(
  private val shell: (serial: String, command: String) -> String? = AdbShell::run
) {
  fun forceStop(serial: String, pkg: String): ActionResult = run(serial, pkg, "Force-stopped") { "am force-stop $it" }

  fun clearData(serial: String, pkg: String): ActionResult {
    if (!valid(pkg)) return ActionResult(false, "Invalid package name")
    val out = shell(serial, "pm clear $pkg")?.trim() ?: return ActionResult(false, "adb did not respond")
    return if (out.startsWith("Success")) ActionResult(true, "Cleared data of $pkg") else ActionResult(false, "pm clear: $out")
  }

  // Только кэш, данные и настройки не трогаются. `pm clear --cache-only` есть не на всех версиях Android;
  // фолбэк — run-as (работает только для debuggable-приложений) с чисткой cache/ и code_cache/.
  fun clearCache(serial: String, pkg: String): ActionResult {
    if (!valid(pkg)) return ActionResult(false, "Invalid package name")
    // --cache-only появился в Android 11 (API 30); на старых `pm clear` с лишним ключом мог стереть ВСЕ данные.
    val sdk = shell(serial, "getprop ro.build.version.sdk")?.trim()?.toIntOrNull() ?: 0
    if (sdk >= 30) {
      val out = shell(serial, "pm clear --cache-only $pkg")?.trim() ?: return ActionResult(false, "adb did not respond")
      if (out.startsWith("Success")) return ActionResult(true, "Cleared cache of $pkg")
    }
    val fallback = shell(serial, "run-as $pkg sh -c 'rm -rf cache/* code_cache/* && echo done'")?.trim().orEmpty()
    return if (fallback.endsWith("done")) {
      ActionResult(true, "Cleared cache of $pkg (internal cache only)")
    } else {
      ActionResult(false, "Cannot clear cache of $pkg: not supported on this Android version and the app is not debuggable")
    }
  }

  fun restart(serial: String, pkg: String): ActionResult {
    if (!valid(pkg)) return ActionResult(false, "Invalid package name")
    shell(serial, "am force-stop $pkg")
    val out = shell(serial, "monkey -p $pkg -c android.intent.category.LAUNCHER 1") ?: return ActionResult(false, "adb did not respond")
    return if ("No activities found" in out) ActionResult(false, "$pkg has no launcher activity") else ActionResult(true, "Restarted $pkg")
  }

  fun openDeepLink(serial: String, uri: String, pkg: String?): ActionResult {
    val u = uri.trim()
    if (u.isEmpty() || u.any { it.isWhitespace() || it == '\'' || it == '"' || it == '`' || it == '\\' }) {
      return ActionResult(false, "Invalid URI")
    }
    val target = if (!pkg.isNullOrBlank() && valid(pkg)) " $pkg" else ""
    // Значение в одинарных кавычках: '&' и '?' в query не должны трактоваться shell'ом.
    val out = shell(serial, "am start -a android.intent.action.VIEW -d '$u'$target") ?: return ActionResult(false, "adb did not respond")
    return if (out.contains("Error") || out.contains("Exception")) {
      ActionResult(false, out.lineSequence().firstOrNull { it.contains("Error") || it.contains("Exception") }.orEmpty().trim())
    } else {
      ActionResult(true, "Opened $u")
    }
  }

  fun appDetails(serial: String, pkg: String): AppDetails? {
    if (!valid(pkg)) return null
    val out = shell(
      serial,
      "dumpsys package $pkg | grep -E 'versionName=|versionCode=|^ +flags=|installerPackageName=|firstInstallTime=|lastUpdateTime='"
    ) ?: return null
    return AppDetailsParser.parse(pkg, out)
  }

  /** PNG-скриншот экрана (`screencap -p` через exec-out, без порчи CRLF). */
  fun screenshot(serial: String): ByteArray? = runCatching {
    val process = ProcessBuilder(AdbLocator.path(), "-s", serial, "exec-out", "screencap", "-p").start()
    val bytes = process.inputStream.readBytes()
    process.waitFor()
    bytes.takeIf { it.size > 8 && it[1] == 'P'.code.toByte() && it[2] == 'N'.code.toByte() }
  }.getOrNull()

  fun saveScreenshot(bytes: ByteArray, dir: File = File(System.getProperty("user.home"), "Pictures/LogCactus")): File {
    dir.mkdirs()
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
    var file = File(dir, "screenshot-$stamp.png")
    var n = 1
    while (file.exists()) file = File(dir, "screenshot-$stamp-${n++}.png")
    return file.also { it.writeBytes(bytes) }
  }

  private fun run(serial: String, pkg: String, done: String, command: (String) -> String): ActionResult {
    if (!valid(pkg)) return ActionResult(false, "Invalid package name")
    val out = shell(serial, command(pkg)) ?: return ActionResult(false, "adb did not respond")
    failureLine(out)?.let { return ActionResult(false, it) }
    return ActionResult(true, "$done $pkg")
  }

  private fun valid(pkg: String) = PACKAGE.matches(pkg)

  companion object {
    private val PACKAGE = Regex("""[\w.]+""")
    private val FAILURE = Regex("""(?i)\b(error|exception|permission denied|not found|unknown package|failure)\b""")

    /** Первая строка вывода shell, похожая на ошибку (adb отдаёт их как обычный текст с кодом 0); null — всё ок. */
    fun failureLine(out: String): String? = out.lineSequence().firstOrNull { FAILURE.containsMatchIn(it) }?.trim()

    /** Показывает файл в Finder / Проводнике; на других ОС открывает его папку. */
    fun revealInFileManager(file: File) {
      runCatching {
        val os = System.getProperty("os.name").lowercase()
        when {
          os.contains("mac") -> ProcessBuilder("open", "-R", file.absolutePath).start()
          os.contains("win") -> ProcessBuilder("explorer", "/select,", file.absolutePath).start()
          else -> java.awt.Desktop.getDesktop().open(file.parentFile)
        }
      }
    }
  }
}
