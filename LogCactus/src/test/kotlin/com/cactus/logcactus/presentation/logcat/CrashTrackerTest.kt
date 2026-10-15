package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashTrackerTest {
  private var t = 1_000L
  private fun e(tag: String, msg: String, pid: Int = 10, level: String = "ERROR") =
    LogEntry(t++, level, tag, pid, "", "", msg)

  private fun javaCrash(pid: Int = 10, ex: String = "java.lang.IllegalStateException: boom", frame: String = "com.a.Main.run(Main.java:10)") = listOf(
    e("AndroidRuntime", "FATAL EXCEPTION: main", pid),
    e("AndroidRuntime", "Process: com.a, PID: $pid", pid),
    e("AndroidRuntime", ex, pid),
    e("AndroidRuntime", "\tat android.os.Handler.handle(Handler.java:1)", pid),
    e("AndroidRuntime", "\tat $frame", pid),
    e("ActivityManager", "Process com.a has died", pid + 1, "INFO")
  )

  @Test
  fun detectsJavaCrashWithSignatureFromAppFrame() {
    val tr = CrashTracker()
    assertTrue(tr.feed(javaCrash()))
    val g = tr.groups().single()
    assertEquals(CrashKind.JAVA, g.kind)
    assertEquals("com.a", g.packageName)
    assertEquals("java.lang.IllegalStateException: boom", g.title)
    assertTrue("com.a.Main.run" in g.signature, g.signature)
    assertEquals(1, g.count)
    assertTrue(g.sample.any { "FATAL EXCEPTION" in it })
  }

  @Test
  fun sameCrashGroupsAndCounts_differentExceptionIsSeparate() {
    val tr = CrashTracker()
    tr.feed(javaCrash() + javaCrash(pid = 20) + javaCrash(ex = "java.lang.NullPointerException: x"))
    val gs = tr.groups()
    assertEquals(2, gs.size)
    assertEquals(2, gs.first { "IllegalState" in it.title }.count)
  }

  @Test
  fun incrementalFeedDoesNotDoubleCount() {
    val tr = CrashTracker()
    val all = ArrayList<LogEntry>(javaCrash())
    tr.feed(all)
    assertFalse(tr.feed(all))
    all.add(e("Other", "noise", 5, "INFO"))
    assertFalse(tr.feed(all))
    assertEquals(1, tr.groups().single().count)
    all.addAll(javaCrash(pid = 30))
    assertTrue(tr.feed(all))
    assertEquals(2, tr.groups().single().count)
  }

  @Test
  fun lastEntryPointsIntoSnapshot() {
    val tr = CrashTracker()
    val list = javaCrash()
    tr.feed(list)
    assertSame(list[0], tr.groups().single().lastEntry)
  }

  @Test
  fun detectsAnrAndNative() {
    val tr = CrashTracker()
    tr.feed(listOf(
      e("ActivityManager", "ANR in com.a (com.a/.Main)", 1),
      e("libc", "Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 123 (Thread-1), pid 123 (a)", 123)
    ))
    val kinds = tr.groups().map { it.kind }.toSet()
    assertEquals(setOf(CrashKind.ANR, CrashKind.NATIVE), kinds)
  }

  @Test
  fun staleIncompleteCrashIsCommittedOnLaterEntries() {
    val tr = CrashTracker()
    tr.feed(listOf(e("AndroidRuntime", "FATAL EXCEPTION: main", 10)))
    assertTrue(tr.groups().isEmpty())
    t += 10_000
    tr.feed(listOf(e("Other", "later", 5, "INFO")))
    assertEquals(1, tr.groups().size)
  }

  @Test
  fun clearedWindowRestartsFromScratch() {
    val tr = CrashTracker()
    tr.feed(javaCrash())
    tr.feed(emptyList())
    tr.feed(javaCrash(pid = 40))
    assertEquals(2, tr.groups().single().count)
  }
}
