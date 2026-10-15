package com.android.tools.idea.logcat.standalone.core

enum class LogLevel(
  val rank: Int,
  val shortName: String
) {
  VERBOSE(0, "V"),
  DEBUG(1, "D"),
  INFO(2, "I"),
  WARN(3, "W"),
  ERROR(4, "E"),
  ASSERT(5, "A"),
  UNKNOWN(-1, "?");

  companion object {
    @JvmStatic
    fun isAtLeast(value: String, threshold: String): Boolean {
      return fromString(value).rank >= fromString(threshold).rank
    }

    @JvmStatic
    fun fromString(value: String): LogLevel {
      val normalized = value.trim().uppercase()
      return entries.firstOrNull { it.name == normalized || it.shortName == normalized } ?: UNKNOWN
    }
  }
}
