package com.cactus.logcactus.presentation.logcat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.android.tools.idea.logcat.standalone.core.LogEntry
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogRenderCacheTest {
  private fun newCache() = LogRenderCache(
    signatureProvider = { "sig" },
    // Текст зависит от предыдущего тега — как hideDuplicateTags.
    lineFormatter = { e, prev -> AnnotatedString(if (e.tag == prev) "  ${e.message}" else "${e.tag} ${e.message}") },
    lineColorProvider = { LevelColors(Color.Black, Color.Black, Color.Black) }
  )

  @Test
  fun slidingWindowRendersSameTextAsFullRebuild() {
    val r = Random(7)
    val cache = newCache()
    var window: List<LogEntry> = emptyList()
    var n = 0
    repeat(2000) { step ->
      repeat(r.nextInt(0, 4)) {
        val next = ArrayList(window)
        next.add(LogEntry(n.toLong(), "INFO", "T${r.nextInt(3)}", 1, "m${n++}"))
        window = if (next.size > 40) ArrayList(next.subList(next.size - 40, next.size)) else next
      }
      val got = cache.render(window).map { it.text.text }
      val want = newCache().render(window).map { it.text.text }
      assertEquals(want, got, "step $step")
    }
  }

  @Test
  fun stableIdsForReusedLines() {
    val cache = newCache()
    val rows = (0 until 5).map { LogEntry(it.toLong(), "INFO", "T$it", 1, "m$it") }
    val first = cache.render(rows)
    val slid = cache.render(rows.subList(1, 5) + LogEntry(9, "INFO", "T9", 1, "m9"))
    // строки 2..4 переиспользованы (тот же id) — LazyColumn не перекомпонует их
    assertEquals(first.subList(2, 5).map { it.id }, slid.subList(1, 4).map { it.id })
  }
}
