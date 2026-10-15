package com.android.tools.idea.logcat.standalone.core

data class LogEntry(
  val timestamp: Long,
  val level: String,
  val tag: String,
  val pid: Int,
  val processName: String,
  val packageName: String,
  val message: String
) {
  constructor(
    timestamp: Long,
    level: String,
    tag: String,
    pid: Int,
    message: String
  ) : this(timestamp, level, tag, pid, "", "", message)
}
