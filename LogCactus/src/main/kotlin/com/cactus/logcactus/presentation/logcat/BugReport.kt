package com.cactus.logcactus.presentation.logcat

import com.cactus.logcactus.standalone.AppVersion
import com.cactus.logcactus.standalone.DeviceInfo
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Markdown-блок для вставки в баг-трекер: шапка (устройство, приложение, дата, фильтр) + строки лога. */
object BugReport {
  private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")

  fun build(
    now: ZonedDateTime,
    device: DeviceInfo?,
    app: AppVersion?,
    filter: String,
    minLevel: String,
    lines: List<String>,
    source: String
  ): String {
    val sb = StringBuilder()
    sb.append("## Bug report\n\n")
    sb.append("- **Date:** ").append(DATE.format(now)).append('\n')
    if (device != null) {
      sb.append("- **Device:** ").append(device.title).append('\n')
      if (device.androidRelease.isNotBlank()) {
        sb.append("- **Android:** ").append(device.androidRelease)
        if (device.sdk.isNotBlank()) sb.append(" (API ").append(device.sdk).append(')')
        sb.append('\n')
      }
      if (device.fingerprint.isNotBlank()) sb.append("- **Build:** ").append(device.fingerprint).append('\n')
    }
    if (app != null) {
      sb.append("- **App:** ").append(app.packageName)
      val version = listOf(app.versionName, app.versionCode.takeIf { it.isNotBlank() }?.let { "($it)" })
        .filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
      if (version.isNotBlank()) sb.append(' ').append(version)
      sb.append('\n')
    }
    val filterText = filter.trim()
    if (filterText.isNotEmpty() || minLevel != "VERBOSE") {
      sb.append("- **Filter:** ")
      if (filterText.isNotEmpty()) sb.append('`').append(filterText.replace('`', '\'')).append('`')
      if (minLevel != "VERBOSE") sb.append(if (filterText.isNotEmpty()) ", " else "").append("level ≥ ").append(minLevel)
      sb.append('\n')
    }
    sb.append("- **Lines:** ").append(lines.size).append(" (").append(source).append(")\n\n")
    sb.append("### Log\n\n")
    // Ограда длиннее любой серии обратных кавычек внутри лога, иначе блок "разорвётся".
    val fence = "`".repeat(maxOf(3, longestBacktickRun(lines) + 1))
    sb.append(fence).append("log\n")
    lines.forEach { sb.append(it.trimEnd()).append('\n') }
    sb.append(fence).append('\n')
    return sb.toString()
  }

  private fun longestBacktickRun(lines: List<String>): Int {
    var best = 0
    for (line in lines) {
      var run = 0
      for (ch in line) {
        if (ch == '`') { run++; if (run > best) best = run } else run = 0
      }
    }
    return best
  }
}
