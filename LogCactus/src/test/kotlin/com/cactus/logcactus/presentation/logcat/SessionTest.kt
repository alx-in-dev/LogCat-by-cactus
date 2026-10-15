package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.standalone.SessionFile
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionTest {
  private fun e(level: String, tag: String, msg: String, ts: Long = 1) = LogEntry(ts, level, tag, 7, "com.a", "com.a", msg)

  @Test
  fun sessionFileRoundTripKeepsTabsNewlinesAndUnicode() {
    val f = File.createTempFile("sess", ".logcactus")
    try {
      val entries = listOf(e("INFO", "T", "a\tb\nc\\d привет ✓", 5), e("ERROR", "Tag", "plain", 6))
      SessionFile.write(f, mapOf("device" to "OnePlus\nCPH", "app" to "com.a 1.0"), entries)
      val s = SessionFile.read(f)!!
      assertEquals(entries, s.entries)
      assertEquals("OnePlus\nCPH", s.meta["device"])
      assertEquals("com.a 1.0", s.meta["app"])
    } finally {
      f.delete()
    }
  }

  @Test
  fun foreignFileIsRejected() {
    val f = File.createTempFile("foreign", ".txt")
    try {
      f.writeText("hello\nworld\n")
      assertNull(SessionFile.read(f))
    } finally {
      f.delete()
    }
  }

  @Test
  fun normalizeStripsNumbersAddressesAndUuids() {
    assertEquals(SessionDiff.normalize("fail id=12 at 0xdeadbeef"), SessionDiff.normalize("fail id=99 at 0x1234"))
    assertEquals(SessionDiff.normalize("x 123e4567-e89b-12d3-a456-426614174000"), SessionDiff.normalize("x 00000000-0000-0000-0000-000000000000"))
  }

  @Test
  fun diffFindsNewGoneAndChanged() {
    val baseline = listOf(e("ERROR", "Net", "timeout 5"), e("ERROR", "Old", "gone")) + (1..12).map { e("INFO", "Chatty", "x") }
    val current = listOf(e("ERROR", "Net", "timeout 9"), e("ERROR", "Db", "corrupted page 4")) +
      (1..40).map { e("INFO", "Chatty", "x") } + e("INFO", "Fresh", "hi")
    val r = SessionDiff.compute(baseline, current)
    assertEquals(listOf("Db: corrupted page #"), r.newErrors.map { it.text })
    assertEquals(listOf("Old: gone"), r.goneErrors.map { it.text })
    assertEquals(listOf("Fresh"), r.newTags.map { it.text }.filter { it == "Fresh" })
    assertTrue(r.changedTags.any { it.startsWith("Chatty: 12") })
    assertTrue(r.hasDifferences)
    assertTrue("### New error types (1)" in SessionDiff.toMarkdown(r, "base", "now"))
  }

  @Test
  fun identicalSessionsHaveNoDifferences() {
    val list = listOf(e("ERROR", "Net", "x"), e("INFO", "A", "y"))
    val r = SessionDiff.compute(list, list)
    assertFalse(r.hasDifferences)
    assertTrue("No significant differences" in SessionDiff.toMarkdown(r, "a", "b"))
  }
}
