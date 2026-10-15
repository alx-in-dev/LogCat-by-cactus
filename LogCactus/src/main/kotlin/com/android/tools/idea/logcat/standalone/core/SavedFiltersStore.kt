package com.android.tools.idea.logcat.standalone.core

interface SavedFiltersStore {
  fun load(): List<SavedLogcatFilter>

  fun save(filters: List<SavedLogcatFilter>)
}
