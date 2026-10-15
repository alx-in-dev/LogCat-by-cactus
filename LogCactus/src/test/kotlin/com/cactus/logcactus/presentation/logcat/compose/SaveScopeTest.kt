package com.cactus.logcactus.presentation.logcat.compose

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveScopeTest {
  @Test
  fun visibleEntriesFollowFilterLevelAndPackage() {
    val entries = listOf(
      LogEntry(1, "INFO", "Net", 1, "a", "com.a", "one"),
      LogEntry(2, "ERROR", "Net", 1, "a", "com.a", "two"),
      LogEntry(3, "INFO", "Db", 2, "b", "com.b", "three"),
      LogEntry(4, "ERROR", "Db", 2, "b", "com.b", "four")
    )
    val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
    bridge.loadSession(SessionFile.Session(emptyMap(), entries))
    repeat(60) { if (!bridge.hasReceivedAnyLogs) Thread.sleep(50) }
    assertTrue(bridge.hasReceivedAnyLogs)

    assertEquals(4, bridge.visibleEntries().size)
    bridge.query = "package:com.a"
    assertEquals(listOf("one", "two"), bridge.visibleEntries().map { it.message })
    bridge.query = "tag:Db"
    bridge.level = "ERROR"
    assertEquals(listOf("four"), bridge.visibleEntries().map { it.message })
  }
}
