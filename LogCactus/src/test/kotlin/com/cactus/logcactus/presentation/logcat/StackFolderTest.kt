package com.cactus.logcactus.presentation.logcat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.android.tools.idea.logcat.standalone.core.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

class StackFolderTest {
  private fun row(msg: String): RenderedLine {
    val e = LogEntry(0L, "ERROR", "T", 1, msg)
    return RenderedLine(AnnotatedString("T  $msg"), msg, Color.Red, "ERROR", Color.Red, Color.White, e)
  }

  private fun trace(frames: Int) = listOf(row("java.lang.IllegalStateException: boom")) +
    (1..frames).map { row("\tat com.a.B.m$it(B.java:$it)") } + row("Caused by: X")

  private val folder = StackFolder()
  private val none: (LogEntry) -> Boolean = { false }

  @Test
  fun collapsesLongFrameRunIntoOnePlaceholder() {
    val rows = trace(10)
    val out = folder.fold(rows, true, none, false)
    assertEquals(3, out.size)
    val ph = out[1]
    assertEquals(RowFold.Kind.STACK, ph.fold?.kind)
    assertEquals(10, ph.fold?.hidden?.size)
    assertTrue("10 stack frames" in ph.message)
  }

  @Test
  fun shortRunStaysAndUnchangedInputIsReturnedAsIs() {
    val rows = trace(2)
    assertSame(rows, folder.fold(rows, true, none, false))
  }

  @Test
  fun disabledFoldingReturnsSameList() {
    val rows = trace(10)
    assertSame(rows, folder.fold(rows, false, none, false))
  }

  @Test
  fun expandedRunShowsHideRowAndFrames() {
    val rows = trace(6)
    val key = rows[1].entry
    val out = folder.fold(rows, true, { it === key }, true)
    assertEquals(rows.size + 1, out.size)
    assertEquals(true, out[1].fold?.expanded)
    assertTrue("hide 6" in out[1].message)
  }

  @Test
  fun placeholderIsStableAcrossFrames() {
    val rows = trace(8)
    val a = folder.fold(rows, true, none, false)[1]
    val b = folder.fold(rows, true, none, false)[1]
    assertSame(a, b)
  }

  @Test
  fun plainLinesRestoresHiddenFramesForCopy() {
    val rows = trace(5)
    val folded = folder.fold(rows, true, none, false)
    assertEquals(rows.map { it.text.text }, StackFolder.plainLines(folded))
  }

  @Test
  fun plainLinesOfExpandedRunHasNoDuplicates() {
    val rows = trace(5)
    val key = rows[1].entry
    val folded = folder.fold(rows, true, { it === key }, true)
    assertEquals(rows.map { it.text.text }, StackFolder.plainLines(folded))
  }

  @Test
  fun toggledJsonRowGetsDetailRowsThatAreNotCopied() {
    val rows = listOf(row("""{"a":1,"b":[true,null]}"""), row("next"))
    val key = rows[0].entry
    val out = folder.fold(rows, false, { it === key }, true, autoDetails = false)
    assertTrue(out.size > rows.size)
    assertEquals(RowFold.Kind.HEAD, out[0].fold?.kind)
    assertEquals(RowFold.Kind.DETAIL, out[1].fold?.kind)
    assertEquals(rows.map { it.text.text }, StackFolder.plainLines(out))
  }

  @Test
  fun autoExpandShowsJsonAfterPrefixAndKeepsPrefixInHeadRow() {
    val rows = listOf(row("""Body: {"data":[{"id":1,"url":"https://x.y/z"}],"ok":true}"""), row("next"))
    val out = folder.fold(rows, false, { false }, false, autoDetails = true)
    assertEquals(RowFold.Kind.HEAD, out[0].fold?.kind)
    assertTrue(out[0].text.text.endsWith("Body: "), out[0].text.text)
    assertTrue(out.drop(1).first().message.startsWith("{"))
    // JSON выровнен по колонке сообщения, то есть под "Body:" (префикс строки в тесте "T  " = 3 символа)
    assertTrue(out[1].text.text.startsWith("   {"), "'" + out[1].text.text + "'")
    assertEquals(rows.map { it.text.text }, StackFolder.plainLines(out))
    // пользователь свернул строку: состояние по умолчанию перевёрнуто
    val key = rows[0].entry
    assertSame(rows, folder.fold(rows, false, { it === key }, true, autoDetails = true))
  }

  @Test
  fun truncatedJsonIsPrettyPrintedWithMarker() {
    val cut = """Body: {"data":[{"id":391439,"attributes":{"title":"Успешная жизнь","image":{"url":"https://a.b/c.jpeg"},"resizes":[{"width":984,"url":"https://a.b/d"""
    val lines = RowDetails.expand(cut)!!
    assertEquals("… (truncated)", lines.last())
    assertTrue(lines.any { "\"title\": \"Успешная жизнь\"" in it })
    assertNull(RowDetails.prettyJson("""{"a":1"""))
    assertNull(RowDetails.expand("List [1] of items here"))
  }

  @Test
  fun frameDetection() {
    assertTrue(StackFolder.isFrame("\tat a.B.c(B.java:1)"))
    assertTrue(StackFolder.isFrame("    at a.B.c(Native Method)"))
    assertTrue(StackFolder.isFrame("\t... 12 more"))
    assertTrue(!StackFolder.isFrame("Caused by: x"))
    assertTrue(!StackFolder.isFrame("at home"))
    assertTrue(!StackFolder.isFrame(""))
  }

  @Test
  fun prettyJsonAndIntent() {
    assertEquals(listOf("{", "  \"a\": 1,", "  \"b\": [", "    true,", "    null", "  ]", "}"), RowDetails.prettyJson("""{"a":1,"b":[true,null]}"""))
    assertEquals(listOf("{}"), RowDetails.prettyJson("{ }"))
    assertEquals(listOf("{", "  \"s\": \"a,b}\"", "}"), RowDetails.prettyJson("""{"s":"a,b}"}"""))
    assertNull(RowDetails.prettyJson("""{"a":1"""))
    assertNull(RowDetails.prettyJson("""{"a":1]"""))
    val intent = RowDetails.expand("Starting: Intent { act=android.intent.action.VIEW dat=https://x.y cmp=com.a/.Main (has extras) }")
    assertNotNull(intent)
    assertTrue("act = android.intent.action.VIEW" in intent!!)
    assertTrue("(has extras)" in intent)
    assertNull(RowDetails.expand("plain message"))
  }
}
