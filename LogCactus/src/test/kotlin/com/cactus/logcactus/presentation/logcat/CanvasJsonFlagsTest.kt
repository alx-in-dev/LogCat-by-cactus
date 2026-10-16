package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Строки раскрытого JSON помечены для карточки: json, первая и последняя; Intent карточкой не рисуется. */
class CanvasJsonFlagsTest {
  private fun entry(msg: String) = LogEntry(1L, "INFO", "T", 1, "p", "p", msg)

  @Test
  fun jsonDetailsAreMarkedWithEdges() {
    val items = CanvasFolder().fold(listOf(entry("""<-- 200 {"a":{"b":1},"c":[1,2]}""")), false, { false }, false, true)
    val details = items.filter { it.kind == CanvasItem.Kind.DETAIL }
    assertTrue(details.size > 2)
    assertTrue(details.all { it.json })
    assertEquals(listOf(true), details.filter { it.first }.map { true })
    assertTrue(details.first().first && !details.first().last)
    assertTrue(details.last().last && !details.last().first)
  }

  @Test
  fun intentDetailsAreNotJson() {
    val items = CanvasFolder().fold(listOf(entry("start Intent { act=android.intent.action.VIEW dat=https://a.b/c flg=0x10000000 }")), false, { false }, false, true)
    val details = items.filter { it.kind == CanvasItem.Kind.DETAIL }
    assertTrue(details.isNotEmpty())
    assertFalse(details.any { it.json })
  }
}

class CanvasJsonFoldTest {
  private fun entry(msg: String) = LogEntry(1L, "INFO", "T", 1, "p", "p", msg)

  @Test
  fun bracketPairsMatchNestedObjects() {
    val lines = RowDetails.prettyJson("""{"a":{"b":1},"c":[1,{"d":2}],"e":{}}""")!!
    val pairs = bracketPairs(lines)
    // { (0) ... } (последняя)
    assertEquals(lines.lastIndex, pairs[0])
    val a = lines.indexOfFirst { it.trim() == "\"a\": {" }
    assertEquals(lines.indexOfFirst { it.trim() == "}," }, pairs[a])
    // пустой объект в одну строку скобкой-открывалкой не считается
    assertFalse(pairs.containsKey(lines.indexOfFirst { it.contains("\"e\": {}") }))
  }

  @Test
  fun collapsedObjectHidesBodyAndKeepsTail() {
    val e = entry("""<-- 200 {"a":{"b":1,"c":2},"d":3}""")
    val folder = CanvasFolder()
    val all = folder.fold(listOf(e), false, { false }, false, true)
    val open = all.first { it.kind == CanvasItem.Kind.DETAIL && it.line.trim() == "\"a\": {" }
    val folded = folder.fold(listOf(e), false, { false }, false, true, collapsed = { setOf(open.lineIndex) })
    val shown = folded.filter { it.kind == CanvasItem.Kind.DETAIL }.map { it.line.trim() }
    assertEquals(listOf("{", "\"a\": { … },", "\"d\": 3", "}"), shown)
    assertTrue(folded.first { it.collapsed }.json)
    // повторная свёртка отдаёт тот же объект (кэш высот и раскладки живёт по идентичности)
    val again = folder.fold(listOf(e), false, { false }, false, true, collapsed = { setOf(open.lineIndex) })
    assertTrue(folded.first { it.collapsed } === again.first { it.collapsed })
  }

  @Test
  fun collapsingRootKeepsOneLineWithBothEdges() {
    val e = entry("""<-- 200 {"a":1,"b":2}""")
    val folded = CanvasFolder().fold(listOf(e), false, { false }, false, true, collapsed = { setOf(0) })
    val d = folded.filter { it.kind == CanvasItem.Kind.DETAIL }
    assertEquals(1, d.size)
    assertEquals("{ … }", d.single().line.trim())
    assertTrue(d.single().first && d.single().last)
  }
}
