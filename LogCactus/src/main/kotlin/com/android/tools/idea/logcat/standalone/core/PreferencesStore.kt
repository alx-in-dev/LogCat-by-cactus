package com.android.tools.idea.logcat.standalone.core

interface PreferencesStore {
  fun load(): LogcatUiPreferences

  fun save(preferences: LogcatUiPreferences)
}
