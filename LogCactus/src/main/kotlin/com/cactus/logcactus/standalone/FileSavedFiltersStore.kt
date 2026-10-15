package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.SavedFiltersStore
import com.android.tools.idea.logcat.standalone.core.SavedLogcatFilter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class FileSavedFiltersStore : SavedFiltersStore {
  private val filePath: Path

  constructor() : this(Paths.get(System.getProperty("user.home"), ".logcactus", "saved-filters.properties"))

  constructor(filePath: Path) {
    this.filePath = filePath
  }

  override fun load(): List<SavedLogcatFilter> {
    if (!Files.exists(filePath)) return emptyList()

    val props = Properties()
    val loaded = runCatching {
      Files.newInputStream(filePath).use { props.load(it) }
    }.isSuccess
    if (!loaded) return emptyList()

    val count = parseInt(props.getProperty("count"), 0)
    val result = ArrayList<SavedLogcatFilter>(count.coerceAtLeast(0))
    for (i in 0 until count) {
      result += SavedLogcatFilter(
        nullable(props.getProperty(key(i, "name"))),
        nullable(props.getProperty(key(i, "message"))),
        parseBoolean(props.getProperty(key(i, "messageRegex")), true),
        nullable(props.getProperty(key(i, "level"))),
        nullable(props.getProperty(key(i, "tag"))),
        parseBoolean(props.getProperty(key(i, "tagRegex")), true),
        nullable(props.getProperty(key(i, "pid"))),
        nullable(props.getProperty(key(i, "package"))),
        parseBoolean(props.getProperty(key(i, "packageRegex")), true)
      )
    }
    return result
  }

  override fun save(filters: List<SavedLogcatFilter>) {
    val parent = filePath.parent ?: return
    if (runCatching { Files.createDirectories(parent) }.isFailure) return

    val props = Properties().apply {
      setProperty("count", filters.size.toString())
      for (i in filters.indices) {
        val f = filters[i]
        put(this, key(i, "name"), f.name)
        put(this, key(i, "message"), f.logMessagePattern)
        setProperty(key(i, "messageRegex"), f.isLogMessageRegex.toString())
        put(this, key(i, "level"), f.logLevel)
        put(this, key(i, "tag"), f.logTagPattern)
        setProperty(key(i, "tagRegex"), f.isLogTagRegex.toString())
        put(this, key(i, "pid"), f.pid)
        put(this, key(i, "package"), f.packageNamePattern)
        setProperty(key(i, "packageRegex"), f.isPackageNameRegex.toString())
      }
    }

    runCatching {
      AtomicFiles.write(filePath) { props.store(it, "LogCactus saved filters") }
    }
  }

  private fun put(props: Properties, key: String, value: String?) {
    if (value != null) props.setProperty(key, value)
  }

  private fun key(index: Int, field: String): String = "filter.$index.$field"

  private fun parseInt(value: String?, defaultValue: Int): Int {
    if (value.isNullOrBlank()) return defaultValue
    return value.trim().toIntOrNull() ?: defaultValue
  }

  private fun parseBoolean(value: String?, defaultValue: Boolean): Boolean {
    if (value.isNullOrBlank()) return defaultValue
    return value.trim().toBoolean()
  }

  private fun nullable(value: String?): String? = if (value.isNullOrEmpty()) null else value
}
