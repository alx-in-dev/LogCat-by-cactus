package com.android.tools.idea.logcat.standalone.core

data class LogcatUiPreferences(
  val minLevel: String,
  val configuredFilterName: String,
  val query: String,
  val isRegex: Boolean,
  val isCaseSensitive: Boolean,
  // Размер буфера логов в байтах (см. LogcatUiState.maxBytes).
  val bufferBytes: Long = LogcatUiState.DEFAULT_BUFFER_BYTES
) {
  companion object {
    @JvmStatic
    fun defaults(): LogcatUiPreferences = LogcatUiPreferences("VERBOSE", "", "", true, false)
  }
}
