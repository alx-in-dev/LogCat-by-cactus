package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry

data class ProcessOption(
  val all: Boolean,
  val label: String,
  val packageName: String,
  val processName: String,
  val pid: Int
) {
  fun matches(entry: LogEntry): Boolean {
    if (all) return true
    if (packageName.isNotBlank() && packageName == entry.packageName) return true
    if (processName.isNotBlank() && processName == entry.processName) return true
    return pid == entry.pid
  }

  companion object {
    fun all(allLabel: String): ProcessOption = ProcessOption(true, allLabel, "", "", -1)

    fun fromEntry(e: LogEntry, allLabel: String): ProcessOption {
      val pkg = e.packageName
      val proc = e.processName
      val pid = e.pid
      val label = buildString {
        append(if (pkg.isBlank()) "pid:$pid" else pkg)
        append(" | ")
        append(if (proc.isBlank()) "pid:$pid" else proc)
        append(" ($pid)")
      }
      if (label == allLabel) return all(allLabel)
      return ProcessOption(false, label, pkg, proc, pid)
    }
  }
}
