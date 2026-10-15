package com.android.tools.idea.logcat.standalone.core

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdbLongLogcatParser {
  private var activeHeader: Header? = null

  fun acceptLine(rawLine: String): List<LogEntry> {
    val line = if (rawLine.indexOf('\r') >= 0) rawLine.replace("\r", "") else rawLine
    // Заголовок всегда начинается с '[' — для остальных строк regex не гоняем.
    val headerMatch = if (line.isNotEmpty() && line[0] == '[') HEADER.matchEntire(line) else null
    if (headerMatch != null) {
      activeHeader = parseHeader(headerMatch)
      return emptyList()
    }

    val header = activeHeader ?: return emptyList()
    if (line.isEmpty()) return emptyList()
    // «--------- beginning of main» — служебная строка adb, а не запись лога.
    if (line.startsWith("--------- beginning of") || line.startsWith("--------- switch to")) return emptyList()

    return listOf(
      LogEntry(
        header.timestamp,
        header.level,
        header.tag,
        header.pid,
        line
      )
    )
  }

  private fun parseHeader(match: MatchResult): Header {
    val monthDay = match.groupValues[1]
    val time = match.groupValues[2]
    val pid = match.groupValues[3].toIntOrNull() ?: 0
    val level = LogLevel.fromString(match.groupValues[5]).name
    val tag = match.groupValues[6]

    var timestamp = System.currentTimeMillis()
    runCatching {
      val md = MonthDay.parse("--$monthDay")
      val now = LocalDateTime.now()
      val parsedTime = LocalTime.parse(time, TIME_FORMAT)
      val dateTime = LocalDateTime.of(
        now.year,
        md.month,
        md.dayOfMonth,
        parsedTime.hour,
        parsedTime.minute,
        parsedTime.second,
        parsedTime.nano
      )
      var at = dateTime.atZone(ZoneId.systemDefault())
      // Лог за 31 декабря, прочитанный 1 января: год не указан, запись из «будущего» относится к прошлому году.
      if (at.toInstant().toEpochMilli() > System.currentTimeMillis() + 24 * 3_600_000L) at = at.minusYears(1)
      timestamp = at.toInstant().toEpochMilli()
    }.onFailure {
      runCatching {
        val parsed = LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.parse(time, TIME_FORMAT))
        timestamp = parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
      }
    }

    return Header(timestamp, level, pid, tag)
  }

  private data class Header(
    val timestamp: Long,
    val level: String,
    val pid: Int,
    val tag: String
  )

  companion object {
    private val HEADER =
      Regex("^\\[\\s*(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+):\\s*(\\d+)\\s+([VDIWEAF])/(.+?)\\s*]$")
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
  }
}
