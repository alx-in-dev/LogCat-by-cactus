package com.android.tools.idea.logcat.standalone.core

data class LogcatFilterCriteria(
  val minLevel: String,
  val query: String,
  val isRegex: Boolean,
  val isCaseSensitive: Boolean,
  // Пакет, в который раскрывается package:mine (приложение на экране устройства).
  val minePackage: String = ""
)
