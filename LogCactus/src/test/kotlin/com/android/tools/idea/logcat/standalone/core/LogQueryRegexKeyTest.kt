package com.android.tools.idea.logcat.standalone.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Как в Logcat Android Studio: без ключа `~` текст ищется как подстрока, регулярка — только через `key~:`. */
class LogQueryRegexKeyTest {
  private fun m(q: String) = LogQueryMatcher(q, regex = false, caseSensitive = false, minePackage = "")
  private fun e(tag: String = "T", msg: String) = LogEntry(1L, "INFO", tag, 1, "p", "p", msg)

  @Test
  fun plainTextIsSubstringNotRegex() {
    assertFalse(m("a.c").matches(e(msg = "abc")))
    assertTrue(m("a.c").matches(e(msg = "a.c")))
    assertTrue(m("(x)").matches(e(msg = "call (x)")))
  }

  @Test
  fun tildeAfterKeyEnablesRegex() {
    assertTrue(m("message~:a.c").matches(e(msg = "abc")))
    assertTrue(m("tag~:Net.*k").matches(e(tag = "NetworkK", msg = "x")))
    assertFalse(m("-tag~:Net.*").matches(e(tag = "Network", msg = "x")))
    assertTrue(m("-tag~:Net.*").matches(e(tag = "Db", msg = "x")))
  }
}
