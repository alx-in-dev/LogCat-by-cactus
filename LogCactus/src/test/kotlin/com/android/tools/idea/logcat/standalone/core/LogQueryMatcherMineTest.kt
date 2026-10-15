package com.android.tools.idea.logcat.standalone.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogQueryMatcherMineTest {
  private fun entry(pkg: String, tag: String = "T") = LogEntry(0L, "INFO", tag, 1, pkg, pkg, "msg")

  private fun matched(query: String, mine: String, vararg pkgs: String): List<String> {
    val m = LogQueryMatcher(query, regex = true, caseSensitive = false, minePackage = mine)
    return pkgs.filter { m.matches(entry(it)) }
  }

  @Test
  fun mineMatchesOnlyExactPackage() {
    assertEquals(
      listOf("com.example.app"),
      matched("package:mine", "com.example.app", "com.example.app", "com.example.app.debug", "com.other")
    )
  }

  @Test
  fun appAliasAndCaseInsensitiveKeyword() {
    assertEquals(listOf("com.a"), matched("app:MINE", "com.a", "com.a", "com.b"))
  }

  @Test
  fun negatedMineExcludesCurrentPackage() {
    assertEquals(listOf("com.b"), matched("!package:mine", "com.a", "com.a", "com.b"))
  }

  @Test
  fun mineCombinesWithOtherTerms() {
    val m = LogQueryMatcher("package:mine tag:Net", regex = true, caseSensitive = false, minePackage = "com.a")
    assertEquals(true, m.matches(entry("com.a", "Net")))
    assertEquals(false, m.matches(entry("com.a", "Db")))
    assertEquals(false, m.matches(entry("com.b", "Net")))
  }

  @Test
  fun unknownMineDoesNotHideEverything() {
    assertEquals(listOf("com.a", "com.b"), matched("package:mine", "", "com.a", "com.b"))
  }

  @Test
  fun packageStartingWithMineWordIsNotRewritten() {
    // "package:mineral" — обычный подстрочный фильтр, а не mine.
    assertEquals(listOf("com.mineral.app"), matched("package:mineral", "com.a", "com.mineral.app", "com.a"))
  }
}
