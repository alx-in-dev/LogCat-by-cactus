package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.LogcatUiPreferences
import com.android.tools.idea.logcat.standalone.core.PreferencesStore

class InMemoryPreferencesStore : PreferencesStore {
  private var preferences: LogcatUiPreferences = LogcatUiPreferences.defaults()

  override fun load(): LogcatUiPreferences = preferences

  override fun save(preferences: LogcatUiPreferences) {
    this.preferences = preferences
  }
}
