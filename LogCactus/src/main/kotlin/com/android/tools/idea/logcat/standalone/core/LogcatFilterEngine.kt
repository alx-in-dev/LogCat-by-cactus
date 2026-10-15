package com.android.tools.idea.logcat.standalone.core

class LogcatFilterEngine {
  fun filter(source: List<LogEntry>, criteria: LogcatFilterCriteria): List<LogEntry> {
    val matcher = LogQueryMatcher(criteria.query, criteria.isRegex, criteria.isCaseSensitive, criteria.minePackage)
    return source.filter { entry ->
      LogLevel.isAtLeast(entry.level, criteria.minLevel) && matcher.matches(entry)
    }
  }
}
