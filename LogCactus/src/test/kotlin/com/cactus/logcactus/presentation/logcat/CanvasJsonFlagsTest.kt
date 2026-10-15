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
