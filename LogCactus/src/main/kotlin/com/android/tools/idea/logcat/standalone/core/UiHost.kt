package com.android.tools.idea.logcat.standalone.core

interface UiHost {
  fun render(state: LogcatUiState)

  fun showError(text: String)
}
