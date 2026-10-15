package com.android.tools.idea.logcat.standalone.core

data class DeviceRef(
  val id: String,
  val displayName: String,
  val androidVersion: String = "",
  val apiLevel: String = "",
  val isEmulator: Boolean = true
)
