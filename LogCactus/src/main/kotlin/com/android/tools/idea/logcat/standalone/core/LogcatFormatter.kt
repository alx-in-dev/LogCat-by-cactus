package com.android.tools.idea.logcat.standalone.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogcatFormatter {
  fun format(entry: LogEntry): String {
    return "[${FORMATTER.format(Instant.ofEpochMilli(entry.timestamp))}] [${entry.level}] ${entry.tag}(${entry.pid}): ${entry.message}"
  }

  companion object {
    private val FORMATTER: DateTimeFormatter =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
  }
}
