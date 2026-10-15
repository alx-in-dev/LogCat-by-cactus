package com.android.tools.idea.logcat.standalone.core

data class SavedLogcatFilter(
  val name: String?,
  val logMessagePattern: String?,
  val isLogMessageRegex: Boolean,
  val logLevel: String?,
  val logTagPattern: String?,
  val isLogTagRegex: Boolean,
  val pid: String?,
  val packageNamePattern: String?,
  val isPackageNameRegex: Boolean
)
