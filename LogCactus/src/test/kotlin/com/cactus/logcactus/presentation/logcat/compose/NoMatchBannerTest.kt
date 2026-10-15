package com.cactus.logcactus.presentation.logcat.compose

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Регрессия 1.3.0: в Canvas-режиме баннер «No logs match» висел при любом фильтре (visibleRows там всегда пуст). */
class NoMatchBannerTest {
  private fun bridgeWithRows(canvas: Boolean): ComposeUiBridge {
    System.setProperty("logcactus.canvas", canvas.toString())
    try {
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      val entries = (1..5).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "line $it") }
      bridge.loadSession(SessionFile.Session(emptyMap(), entries))
      repeat(60) { if (!bridge.hasReceivedAnyLogs) Thread.sleep(50) }
      return bridge
    } finally {
      System.setProperty("logcactus.canvas", "false")
    }
  }

  @Test
  fun rowsAreReportedInCanvasMode() {
    val bridge = bridgeWithRows(canvas = true)
    assertTrue(bridge.canvasMode)
    assertTrue(bridge.visibleRows.isEmpty(), "canvas mode does not build RenderedLine rows")
    assertTrue(bridge.hasVisibleRows, "but the list is not empty")
  }

  @Test
  fun rowsAreReportedInLegacyMode() {
    val bridge = bridgeWithRows(canvas = false)
    assertFalse(bridge.canvasMode)
    assertTrue(bridge.hasVisibleRows)
  }
}
