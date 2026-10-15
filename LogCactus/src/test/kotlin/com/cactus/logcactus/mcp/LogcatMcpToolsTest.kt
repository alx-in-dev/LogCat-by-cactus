package com.cactus.logcactus.mcp

import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.presentation.logcat.CrashGroup
import com.cactus.logcactus.presentation.logcat.CrashKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogcatMcpToolsTest {
  private fun e(level: String, tag: String, msg: String, t: Long = 0) = LogEntry(t, level, tag, 1, "proc", "com.app", msg)

  private class Fake(val visible: List<LogEntry>, val buffer: List<LogEntry> = visible, var crashes: List<CrashGroup> = emptyList()) : McpLogcatSource {
    var windowQuery = ""
    var mine = ""
    var shot: ByteArray? = null
    override fun devices() = listOf(DeviceRef("s1", "Pixel", "14", "34", false))
    override fun selectedDeviceId() = "s1"
    override fun viewFilter() = McpLogcatSource.ViewFilter(windowQuery, "VERBOSE", mine)
    override fun entries(allBuffer: Boolean) = if (allBuffer) buffer else visible
    override fun crashes() = crashes
    override fun bookmarkLines() = listOf("bm line")
    override fun bugReport(lines: List<String>, source: String) = "REPORT($source):" + lines.joinToString("|")
    override fun screenshot() = shot
  }

  private fun tool(src: McpLogcatSource, name: String) = LogcatMcpTools(src).all().first { it.name == name }
  private fun args(vararg kv: Pair<String, Any>): JsonObject = buildJsonObject {
    kv.forEach { (k, v) -> if (v is Int) put(k, v) else put(k, JsonPrimitive(v.toString())) }
  }
  private fun text(r: McpToolResult) = r.content.first()["text"]!!.jsonPrimitive.content

  @Test fun getLogsFiltersAndKeepsNewestLast() {
    val src = Fake(listOf(e("D", "A", "one"), e("E", "B", "two"), e("E", "A", "three")))
    val out = text(tool(src, "get_logs").handler(args("filter" to "tag:A")))
    assertTrue(out.startsWith("2 of 2 matching"), out)
    assertTrue(out.indexOf("one") < out.indexOf("three"))
    assertFalse(out.contains("two"))
  }

  @Test fun getLogsLevelParamAndLimitTakeNewest() {
    val src = Fake(listOf(e("D", "A", "dbg"), e("E", "A", "err1"), e("E", "A", "err2")))
    val out = text(tool(src, "get_logs").handler(args("level" to "ERROR", "limit" to 1)))
    assertTrue(out.startsWith("1 of 2 matching"), out)
    assertTrue(out.contains("err2") && !out.contains("err1") && !out.contains("dbg"))
  }

  @Test fun scopeBufferIgnoresWindowFilterAndMineUsesWindowPackage() {
    val src = Fake(visible = listOf(e("E", "A", "seen")), buffer = listOf(e("E", "A", "seen"), e("I", "Z", "hidden")))
    assertTrue(text(tool(src, "get_logs").handler(args("scope" to "buffer"))).contains("hidden"))
    assertFalse(text(tool(src, "get_logs").handler(args())).contains("hidden"))
    src.mine = "com.app"
    assertTrue(text(tool(src, "get_logs").handler(args("scope" to "buffer", "filter" to "package:mine"))).startsWith("2 of 2"))
  }

  @Test fun hugeOutputIsCutFromTheOldEnd() {
    val big = "x".repeat(1000)
    val src = Fake((1..500).map { e("I", "T", "$it $big") })
    val out = text(tool(src, "get_logs").handler(args("limit" to 500)))
    assertTrue(out.length < 62_000)
    assertTrue(out.contains("oldest dropped"))
    assertTrue(out.contains("500 $big".take(20)))
  }

  @Test fun crashesAndEmptyCase() {
    val src = Fake(emptyList())
    assertEquals("No crashes or ANRs in the buffer.", text(tool(src, "get_crashes").handler(args())))
    val g = CrashGroup(CrashKind.JAVA, "sig", "NPE in Foo", "com.app", 1000, listOf("FATAL EXCEPTION", "\tat Foo.bar"), 3, 2000, e("E", "AndroidRuntime", "x"))
    src.crashes = listOf(g)
    val out = text(tool(src, "get_crashes").handler(args()))
    assertTrue(out.contains("JAVA ×3: NPE in Foo") && out.contains("at Foo.bar"))
  }

  @Test fun bugReportModes() {
    val src = Fake(listOf(e("E", "A", "boom")))
    assertTrue(text(tool(src, "get_bug_report").handler(args())).startsWith("REPORT(last visible):"))
    assertEquals("REPORT(bookmarked):bm line", text(tool(src, "get_bug_report").handler(args("mode" to "bookmarks"))))
    assertTrue(tool(src, "get_bug_report").handler(args("mode" to "crashes")).isError) // нет крашей — нечего отдавать
    assertTrue(tool(src, "get_bug_report").handler(args("mode" to "weird")).isError)
  }

  @Test fun screenshotReturnsImageBlockOrError() {
    val src = Fake(emptyList())
    assertTrue(tool(src, "take_screenshot").handler(args()).isError)
    src.shot = byteArrayOf(1, 2, 3)
    val r = tool(src, "take_screenshot").handler(args())
    assertEquals("image", r.content[1]["type"]!!.jsonPrimitive.content)
    assertEquals("AQID", r.content[1]["data"]!!.jsonPrimitive.content)
  }

  @Test fun listDevicesMarksSelected() {
    val out = text(tool(Fake(emptyList()), "list_devices").handler(args()))
    assertTrue(out.contains("* s1 — Pixel, Android 14 (API 34), physical"), out)
  }
}
