package com.cactus.logcactus.standalone

/**
 * Разбор вывода `dumpsys` в пакет приложения, которое сейчас на экране устройства.
 * Формат отличается между версиями Android, поэтому паттерны терпимые.
 */
object ForegroundAppParser {
  // Android 10+: "topResumedActivity=ActivityRecord{1a2b u0 com.pkg/.Main t12}"
  // Раньше:      "mResumedActivity: ActivityRecord{1a2b u0 com.pkg/com.pkg.Main t12}"
  private val RESUMED = Regex("""(?:topResumedActivity|mResumedActivity|ResumedActivity)[=:]\s*ActivityRecord\{[^}]*?\s([\w.]+)/[\w.$]+""")

  // "mCurrentFocus=Window{1a2b u0 com.pkg/com.pkg.Main}"
  private val FOCUS = Regex("""(?:mCurrentFocus|mFocusedApp)=\S*\{[^}]*?\s([\w.]+)/[\w.$]+""")

  fun parseResumed(dump: String): String? = RESUMED.find(dump)?.groupValues?.get(1)

  fun parseFocus(dump: String): String? = FOCUS.find(dump)?.groupValues?.get(1)

  // Экран, лаунчер, шторка и т.п. — не "то приложение, с которым работаю".
  fun isSystemShell(pkg: String): Boolean {
    val p = pkg.lowercase()
    return p in SYSTEM_SHELL || p.contains("launcher") || p.contains("inputmethod")
  }

  private val SYSTEM_SHELL = setOf(
    "com.android.systemui",
    "com.android.settings",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "android"
  )
}

/** Спрашивает у устройства, какое приложение сейчас на переднем плане. */
class ForegroundAppTracker(
  private val shell: (serial: String, command: String) -> String? = AdbShell::run
) {
  // Пакет приложения на экране; null — не удалось определить.
  // Системные оболочки возвращаются как есть: решать, игнорировать ли их, должен вызывающий.
  fun current(serial: String): String? {
    val resumed = shell(serial, "dumpsys activity activities | grep -E 'ResumedActivity'")
      ?.let(ForegroundAppParser::parseResumed)
    if (resumed != null) return resumed
    return shell(serial, "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
      ?.let(ForegroundAppParser::parseFocus)
  }
}
