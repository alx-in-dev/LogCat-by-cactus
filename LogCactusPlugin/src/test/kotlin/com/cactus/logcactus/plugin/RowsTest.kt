package com.cactus.logcactus.plugin

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.plugin.adb.AdbClient
import com.cactus.logcactus.plugin.ui.Row
import com.cactus.logcactus.plugin.ui.buildRows
import com.cactus.logcactus.plugin.ui.copyLines
import com.cactus.logcactus.plugin.ui.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowsTest {
  private fun e(msg: String) = LogEntry(0L, "ERROR", "T", 1, "p", "com.a", msg)

  @Test
  fun stackFramesFoldIntoOneRowAndUnfoldBackWhenCopied() {
    val frames = (1..5).map { e("\tat com.a.B.m$it(B.java:$it)") }
    val rows = buildRows(listOf(e("boom")) + frames, foldStacks = true, autoJson = true, toggled = emptySet())
    assertEquals(2, rows.size)
    assertTrue(rows[1] is Row.Stack)
    assertEquals(6, copyLines(rows).size)
  }

  @Test
  fun toggledStackShowsEveryFrame() {
    val frames = (1..5).map { e("\tat com.a.B.m$it(B.java:$it)") }
    val first = frames.first()
    val rows = buildRows(frames, foldStacks = true, autoJson = true, toggled = setOf(key(first)))
    assertEquals(6, rows.size)
  }

  @Test
  fun jsonExpandsAutomaticallyAndTogglesOff() {
    val json = e("""Body: {"a":1,"b":[1,2]}""")
    val open = buildRows(listOf(json), foldStacks = true, autoJson = true, toggled = emptySet())
    assertTrue(open.size > 1 && open.drop(1).all { it is Row.Detail })
    val closed = buildRows(listOf(json), foldStacks = true, autoJson = true, toggled = setOf(key(json)))
    assertEquals(1, closed.size)
  }

  @Test
  fun parsesAdbDevicesOutput() {
    val out = "List of devices attached\nemulator-5554          device product:sdk model:Pixel_7 device:emu\n192.168.0.5:5555 unauthorized\n"
    val d = AdbClient.parseDevices(out)
    assertEquals(2, d.size)
    assertEquals("Pixel 7 (emulator-5554)", d[0].title)
    assertTrue(d[0].online)
    assertTrue(!d[1].online)
  }

  @Test
  fun longUrlIsSplitIntoDecodedParameters() {
    val url = "https://yandex.ru/adfox/264443/event?adfox-version=1&efc=1&eid5=%24%24VISITOR_ID%24%24&hash=b82813e76f6c05d7&initial-engine-id=hcb&app=com.ctc&rand=gotqjlo&stat-id=0&puid47=[ERRORCODE]"
    val msg = "error_urls=[$url]"
    val rows = buildRows(listOf(e(msg)), foldStacks = true, autoJson = true, toggled = emptySet())
    assertTrue(rows.size > 5)
    val head = (rows[0] as Row.Entry).headText!!
    assertTrue(head.startsWith("error_urls=[https://yandex.ru/adfox/264443/event?…"))
    val details = rows.drop(1).map { (it as Row.Detail).text }
    assertTrue(details.contains("  eid5 = \$\$VISITOR_ID\$\$"))
    assertTrue(details.contains("  puid47 = [ERRORCODE]"))
    val closed = buildRows(listOf(e(msg)), foldStacks = true, autoJson = true, toggled = emptySet(), autoUrl = false)
    assertEquals(1, closed.size)
  }
}
