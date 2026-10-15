package com.cactus.logcactus.presentation.logcat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlDetailsTest {
  private val url = "https://yandex.ru/adfox/264443/event?adfox-version=1&efc=1&eid5=%24%24VISITOR_ID%24%24&hash=b82813e76f6c05d7&initial-engine-id=hcb&app=com.ctc&rand=gotqjlo&stat-id=0&puid47=[ERRORCODE]"

  @Test
  fun longUrlIsSplitIntoDecodedParameters() {
    val e = UrlDetails.expansion("error_urls=[$url]")!!
    assertEquals("https://yandex.ru/adfox/264443/event", e.lines.first())
    assertTrue(e.lines.contains("  eid5 = \$\$VISITOR_ID\$\$"))
    assertTrue(e.lines.contains("  puid47 = [ERRORCODE]"))
    assertEquals("error_urls=[https://yandex.ru/adfox/264443/event", ("error_urls=[$url]").substring(0, e.start))
  }

  @Test
  fun shortUrlsAndPlainMessagesAreLeftAlone() {
    assertNull(UrlDetails.expansion("GET https://a.b/c?x=1 done"))
    assertNull(UrlDetails.expansion("just a very long line without links ".repeat(10)))
  }

  @Test
  fun rowDetailsExposesTheUrlExpansion() {
    val msg = "error_urls=[$url]"
    assertTrue(RowDetails.mayBeExpandable(msg))
    val exp = RowDetails.expansion(msg)!!
    assertTrue(exp.lines.size > 5)
    assertEquals(msg.indexOf('?'), exp.start)
  }

  @Test
  fun canvasFolderTurnsALongUrlIntoHeadAndDetailRows() {
    val entry = com.android.tools.idea.logcat.standalone.core.LogEntry(0L, "DEBUG", "T", 1, "p", "com.a", "error_urls=[$url]")
    val items = CanvasFolder().fold(listOf(entry), foldStacks = true, isToggled = { false }, anyToggled = false, autoDetails = true)
    assertEquals(CanvasItem.Kind.HEAD, items.first().kind)
    assertTrue(items.drop(1).all { it.kind == CanvasItem.Kind.DETAIL } && items.size > 5)
    val collapsed = CanvasFolder().fold(listOf(entry), foldStacks = true, isToggled = { false }, anyToggled = false, autoDetails = false)
    assertEquals(1, collapsed.size)
  }

  @Test
  fun urlsHaveTheirOwnAutoExpandSwitchIndependentOfJson() {
    val u = com.android.tools.idea.logcat.standalone.core.LogEntry(0L, "DEBUG", "T", 1, "p", "com.a", "error_urls=[$url]")
    val j = com.android.tools.idea.logcat.standalone.core.LogEntry(0L, "DEBUG", "T", 2, "p", "com.a", """Body: {"a":1,"b":[1,2]}""")
    val rows = listOf(u, j)
    // JSON раскрыт, URL — нет
    val jsonOnly = CanvasFolder().fold(rows, foldStacks = true, isToggled = { false }, anyToggled = false, autoDetails = true, autoUrls = false)
    assertEquals(1, jsonOnly.count { it.entry === u })
    assertTrue(jsonOnly.count { it.entry === j } > 1)
    // URL раскрыт, JSON — нет
    val urlOnly = CanvasFolder().fold(rows, foldStacks = true, isToggled = { false }, anyToggled = false, autoDetails = false, autoUrls = true)
    assertTrue(urlOnly.count { it.entry === u } > 1)
    assertEquals(1, urlOnly.count { it.entry === j })
  }
}
