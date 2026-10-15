package com.cactus.logcactus.standalone

data class DeviceInfo(
  val manufacturer: String,
  val model: String,
  val androidRelease: String,
  val sdk: String,
  val fingerprint: String
) {
  val title: String
    get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "unknown device" }
}

data class AppVersion(val packageName: String, val versionName: String, val versionCode: String)

object DeviceInfoParser {
  // Значения приходят строками в порядке запроса (по одной на строку).
  fun parseProps(output: String): DeviceInfo {
    val lines = output.lines().map { it.trim() }
    fun at(i: Int) = lines.getOrElse(i) { "" }
    return DeviceInfo(at(0), at(1), at(2), at(3), at(4))
  }

  // dumpsys package: "versionCode=456 minSdk=24 targetSdk=34" и "versionName=1.2.3"
  private val VERSION_CODE = Regex("""versionCode=(\d+)""")
  private val VERSION_NAME = Regex("""versionName=(\S+)""")

  fun parseVersion(pkg: String, dump: String): AppVersion? {
    val name = VERSION_NAME.find(dump)?.groupValues?.get(1)
    val code = VERSION_CODE.find(dump)?.groupValues?.get(1)
    if (name == null && code == null) return null
    return AppVersion(pkg, name.orEmpty(), code.orEmpty())
  }
}

/** Собирает сведения об устройстве и версии приложения для баг-репорта (два вызова adb). */
class DeviceInfoCollector(
  private val shell: (serial: String, command: String) -> String? = AdbShell::run
) {
  fun device(serial: String): DeviceInfo? {
    val out = shell(
      serial,
      "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; " +
        "getprop ro.build.version.sdk; getprop ro.build.fingerprint"
    ) ?: return null
    return DeviceInfoParser.parseProps(out)
  }

  fun app(serial: String, pkg: String): AppVersion? {
    // Пакет приходит из вывода dumpsys, но на всякий случай не даём ему вставить произвольный shell.
    if (!pkg.matches(PACKAGE)) return null
    val out = shell(serial, "dumpsys package $pkg | grep -E 'versionName=|versionCode='") ?: return null
    return DeviceInfoParser.parseVersion(pkg, out)
  }

  private companion object {
    val PACKAGE = Regex("""[\w.]+""")
  }
}
