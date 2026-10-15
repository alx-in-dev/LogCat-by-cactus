package com.android.tools.idea.logcat.standalone.core

sealed interface LogcatAction {
  data class ApplyFilter(
    val level: String,
    val configuredFilterName: String,
    val query: String,
    val isRegex: Boolean,
    val isCaseSensitive: Boolean
  ) : LogcatAction

  class ClearLogs : LogcatAction

  data class SetBufferSize(val bytes: Long) : LogcatAction
}
