package com.cactus.logcactus.presentation.logcat.compose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonHighlightTest {
  private val c = JsonColors.DARK

  private fun colorsOf(prefix: String, line: String): List<Pair<String, androidx.compose.ui.graphics.Color>> {
    val a = JsonHighlight.line(prefix, line, c)
    return a.spanStyles.map { a.text.substring(it.start, it.end) to it.item.color }
  }

  @Test
  fun keyAndNumber() {
    assertEquals(listOf("\"retryAfterMs\"" to c.key, "2000" to c.number), colorsOf("", "  \"retryAfterMs\": 2000"))
  }

  @Test
  fun stringValueWithEscapedQuoteAndColonInside() {
    assertEquals(listOf("\"a\"" to c.key, "\"x \\\" : y\"" to c.string), colorsOf("", "\"a\": \"x \\\" : y\","))
  }

  @Test
  fun keywordsAndNegativeNumbers() {
    assertEquals(listOf("true" to c.keyword), colorsOf("", "true,"))
    assertEquals(listOf("null" to c.keyword), colorsOf("", "  null"))
    assertEquals(listOf("-1.5e3" to c.number), colorsOf("", "-1.5e3"))
  }

  @Test
  fun bracketsStayPlainAndPrefixIsKept() {
    val a = JsonHighlight.line("    ", "{", c)
    assertEquals("    {", a.text)
    assertEquals(0, a.spanStyles.size)
  }
}
