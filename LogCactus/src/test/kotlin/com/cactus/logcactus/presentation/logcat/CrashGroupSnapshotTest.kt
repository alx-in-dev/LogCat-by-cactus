package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashGroupSnapshotTest {
  private fun e(t: Long, tag: String, msg: String, pid: Int = 5) = LogEntry(t, "ERROR", tag, pid, "p", "com.a", msg)

  @Test
  fun earlierSnapshotDoesNotChangeWhenTheSameCrashRepeats() {
    val tracker = CrashTracker()
    val anr1 = e(1000, "ActivityManager", "ANR in com.a (com.a/.Main)")
    tracker.feed(listOf(anr1))
    val before = tracker.groups()
    tracker.feed(listOf(anr1, e(9000, "ActivityManager", "ANR in com.a (com.a/.Main)")))
    val after = tracker.groups()
    assertEquals(1, before.single().count)
    assertEquals(2, after.single().count)
  }

  @Test
  fun nativeCrashReportedByLibcAndDebugIsOneGroup() {
    val tracker = CrashTracker()
    val m = "Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 5 (Main), pid 5 (com.a)"
    tracker.feed(listOf(e(1000, "libc", m), e(1010, "DEBUG", m)))
    assertEquals(1, tracker.groups().single().count)
    assertTrue(tracker.groups().single().signature.contains("com.a"))
  }
}

class DeviceActionsFailureTest {
  private val actions = { out: String -> com.cactus.logcactus.standalone.DeviceActions(shell = { _, _ -> out }) }

  @Test
  fun adbErrorTextIsNotReportedAsSuccess() {
    assertEquals(false, actions("Error type 3\nError: Activity class does not exist").forceStop("s", "com.a").ok)
    assertEquals(true, actions("").forceStop("s", "com.a").ok)
  }

  @Test
  fun cacheClearOnOldAndroidNeverUsesPmClearFlag() {
    val seen = ArrayList<String>()
    val a = com.cactus.logcactus.standalone.DeviceActions(shell = { _, c -> seen.add(c); if (c.startsWith("getprop")) "28" else "done" })
    assertEquals(true, a.clearCache("s", "com.a").ok)
    assertTrue(seen.none { it.contains("--cache-only") })
  }

  @Test
  fun screenshotsSavedInTheSameSecondDoNotOverwrite() {
    val dir = java.nio.file.Files.createTempDirectory("shots").toFile()
    val a = com.cactus.logcactus.standalone.DeviceActions()
    val f1 = a.saveScreenshot(byteArrayOf(1), dir)
    val f2 = a.saveScreenshot(byteArrayOf(2), dir)
    assertTrue(f1 != f2)
  }
}
