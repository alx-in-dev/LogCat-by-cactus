package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanvasFolderTest {
  private fun e(msg: String) = LogEntry(0L, "ERROR", "T", 1, msg)

  private fun trace(frames: Int) =
    listOf(e("java.lang.IllegalStateException: boom")) + (1..frames).map { e("\tat com.a.B.m$it(B.java:$it)") } + e("Caused by: X")

  private val none: (LogEntry) -> Boolean = { false }

  @Test
  fun longFrameRunBecomesOnePlaceholderWithHiddenFrames() {
    val rows = trace(10)
    val items = CanvasFolder().fold(rows, true, none, false, false)
    assertEquals(3, items.size)
    val stack = items[1]
    assertEquals(CanvasItem.Kind.STACK, stack.kind)
    assertEquals(10, stack.hidden.size)
    assertTrue(stack.covers(rows[5]))
    assertSame(rows[10], stack.lastEntry)
  }

  @Test
  fun shortRunAndDisabledFoldingKeepEveryEntry() {
    assertEquals(trace(2).size, CanvasFolder().fold(trace(2), true, none, false, false).size)
    assertEquals(trace(10).size, CanvasFolder().fold(trace(10), false, none, false, false).size)
  }

  @Test
  fun expandedStackShowsPlaceholderAndFrames() {
    val rows = trace(6)
    val first = rows[1]
    val items = CanvasFolder().fold(rows, true, { it === first }, true, false)
    assertEquals(rows.size + 1, items.size)
    assertTrue(items[1].expanded && items[1].kind == CanvasItem.Kind.STACK)
    assertEquals(CanvasItem.Kind.ENTRY, items[2].kind)
  }

  @Test
  fun itemsAreStableAcrossRebuilds() {
    val rows = trace(8)
    val folder = CanvasFolder()
    val a = folder.fold(rows, true, none, false, false)
    val b = folder.fold(rows, true, none, false, false)
    a.indices.forEach { assertSame(a[it], b[it]) }
  }

  @Test
  fun jsonAfterPrefixIsExpandedIntoHeadAndDetailRows() {
    val json = e("""Body: {"data":[{"id":1}],"ok":true}""")
    val items = CanvasFolder().fold(listOf(e("before"), json, e("after")), false, none, false, autoDetails = true)
    assertEquals(CanvasItem.Kind.HEAD, items[1].kind)
    assertEquals("Body: ".length, items[1].payloadStart)
    assertTrue(items.drop(2).takeWhile { it.kind == CanvasItem.Kind.DETAIL }.isNotEmpty())
    assertEquals(CanvasItem.Kind.ENTRY, items.last().kind)
    // пользователь свернул: состояние по умолчанию перевёрнуто
    val collapsed = CanvasFolder().fold(listOf(json), false, { it === json }, true, autoDetails = true)
    assertEquals(listOf(CanvasItem.Kind.ENTRY), collapsed.map { it.kind })
  }

  @Test
  fun bookmarkedFrameInsideCollapsedStackIsCoveredByPlaceholder() {
    val rows = trace(6)
    val stack = CanvasFolder().fold(rows, true, none, false, false)[1]
    assertTrue(stack.covers(rows[3]))
    assertTrue(!stack.covers(rows[0]))
  }
}
