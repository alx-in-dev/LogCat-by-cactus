package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.LogcatUiPreferences
import com.android.tools.idea.logcat.standalone.core.LogcatUiState
import com.android.tools.idea.logcat.standalone.core.PreferencesStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class FilePreferencesStore : PreferencesStore {
  private val filePath: Path

  constructor() : this(Paths.get(System.getProperty("user.home"), ".logcactus", "preferences.properties"))

  constructor(filePath: Path) {
    this.filePath = filePath
  }

  override fun load(): LogcatUiPreferences {
    if (!Files.exists(filePath)) return LogcatUiPreferences.defaults()

    val props = Properties()
    val loaded = runCatching {
      Files.newInputStream(filePath).use { props.load(it) }
    }.isSuccess
    if (!loaded) return LogcatUiPreferences.defaults()

    val level = nonBlankOrDefault(props.getProperty("minLevel"), "VERBOSE")
    val configured = nonNull(props.getProperty("configuredFilterName"))
    val query = nonNull(props.getProperty("query"))
    val regex = parseBoolean(props.getProperty("regex"), true)
    val caseSensitive = parseBoolean(props.getProperty("caseSensitive"), false)
    val buffer = props.getProperty("bufferBytes")?.trim()?.toLongOrNull()?.takeIf { it >= LogcatUiState.MIN_BUFFER_BYTES }
      ?: LogcatUiState.DEFAULT_BUFFER_BYTES
    return LogcatUiPreferences(level, configured, query, regex, caseSensitive, buffer)
  }

  override fun save(preferences: LogcatUiPreferences) {
    val parent = filePath.parent ?: return
    if (runCatching { Files.createDirectories(parent) }.isFailure) return

    val props = Properties().apply {
      setProperty("minLevel", preferences.minLevel)
      setProperty("configuredFilterName", preferences.configuredFilterName)
      setProperty("query", preferences.query)
      setProperty("regex", preferences.isRegex.toString())
      setProperty("caseSensitive", preferences.isCaseSensitive.toString())
      setProperty("bufferBytes", preferences.bufferBytes.toString())
    }

    runCatching {
      AtomicFiles.write(filePath) { props.store(it, "LogCactus preferences") }
    }
  }

  private fun nonNull(value: String?): String = value ?: ""

  private fun nonBlankOrDefault(value: String?, fallback: String): String =
    if (value.isNullOrBlank()) fallback else value.trim()

  private fun parseBoolean(value: String?, fallback: Boolean): Boolean =
    if (value.isNullOrBlank()) fallback else value.trim().toBoolean()
}
