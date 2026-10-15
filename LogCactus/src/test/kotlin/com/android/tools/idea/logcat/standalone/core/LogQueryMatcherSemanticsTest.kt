package com.android.tools.idea.logcat.standalone.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogQueryMatcherSemanticsTest {
  private fun e(tag: String = "T", level: String = "INFO", msg: String = "msg") = LogEntry(0L, level, tag, 1, "p", "com.a", msg)
  private fun m(q: String, regex: Boolean = true) = LogQueryMatcher(q, regex = regex, caseSensitive = false, minePackage = "")

  @Test
  fun explicitAmpersandIsAnd() {
    assertFalse(m("tag:a & tag:b").matches(e(tag = "a")))
    assertTrue(m("tag:a & tag:a").matches(e(tag = "a")))
  }

  @Test
  fun adjacentSameKeyTermsStayOr() {
    assertTrue(m("tag:a tag:b").matches(e(tag = "b")))
  }

  @Test
  fun unknownKeyIsPlainText() {
    assertTrue(m("http://x", regex = false).matches(e(msg = "GET http://x/y")))
    assertFalse(m("http://x", regex = false).matches(e(msg = "other")))
  }

  @Test
  fun levelIsMinimumLevel() {
    assertTrue(m("level:W").matches(e(level = "ERROR")))
    assertFalse(m("level:W").matches(e(level = "INFO")))
  }

  @Test
  fun invalidRegexFallsBackToLiteral() {
    assertTrue(m("foo(").matches(e(msg = "call foo( now")))
  }
}
