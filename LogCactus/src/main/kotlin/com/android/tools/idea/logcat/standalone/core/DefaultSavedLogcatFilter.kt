package com.android.tools.idea.logcat.standalone.core

import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class DefaultSavedLogcatFilter private constructor(
  val name: String,
  private val messagePattern: Pattern?,
  private val tagPattern: Pattern?,
  private val packagePattern: Pattern?,
  private val pid: String?,
  private val minLevel: String?
) {
  fun isApplicable(entry: LogEntry): Boolean {
    if (!minLevel.isNullOrEmpty() && !LogLevel.isAtLeast(entry.level, minLevel)) return false
    if (messagePattern != null && !messagePattern.matcher(entry.message).find()) return false
    if (tagPattern != null && !tagPattern.matcher(entry.tag).find()) return false
    if (packagePattern != null && !packagePattern.matcher(entry.packageName).find()) return false
    if (!pid.isNullOrBlank() && pid != entry.pid.toString()) return false
    return true
  }

  companion object {
    @JvmStatic
    fun compile(filter: SavedLogcatFilter): DefaultSavedLogcatFilter {
      val name = filter.name ?: ""
      val message = compilePattern(filter.logMessagePattern, filter.isLogMessageRegex)
      val tag = compilePattern(filter.logTagPattern, filter.isLogTagRegex)
      val pkg = compilePattern(filter.packageNamePattern, filter.isPackageNameRegex)
      val level = normalizeLevel(filter.logLevel)
      return DefaultSavedLogcatFilter(name, message, tag, pkg, filter.pid, level)
    }

    private fun compilePattern(value: String?, regex: Boolean): Pattern? {
      if (value.isNullOrEmpty()) return null

      var flags = Pattern.CASE_INSENSITIVE
      if (hasUppercase(value) && hasLowercase(value)) {
        flags = 0
      }
      if (regex) {
        try {
          return Pattern.compile(value, flags)
        } catch (_: PatternSyntaxException) {
        }
      }
      return Pattern.compile(Pattern.quote(value), flags)
    }

    private fun hasUppercase(text: String): Boolean = text.any { it.isUpperCase() }

    private fun hasLowercase(text: String): Boolean = text.any { it.isLowerCase() }

    private fun normalizeLevel(level: String?): String? {
      if (level.isNullOrBlank()) return null
      return level.trim().uppercase(Locale.ROOT)
    }
  }
}
