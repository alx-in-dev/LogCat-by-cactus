package com.cactus.logcactus.application.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.presentation.logcat.ProcessOption

class ProcessOptionsService(
  private val allLabel: String
) {
  fun build(source: List<LogEntry>, includeNoPackage: Boolean): List<ProcessOption> {
    val seen = LinkedHashMap<String, ProcessOption>()
    seen[allLabel] = ProcessOption.all(allLabel)
    source.forEach { entry ->
      val option = ProcessOption.fromEntry(entry, allLabel)
      if (includeNoPackage || option.packageName.isNotBlank()) {
        seen.putIfAbsent(option.label, option)
      }
    }
    return seen.values.sortedWith(compareBy<ProcessOption> { if (it.all) 0 else 1 }.thenBy { it.label.lowercase() })
  }
}
