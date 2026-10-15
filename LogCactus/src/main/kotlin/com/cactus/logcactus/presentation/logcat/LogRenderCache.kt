package com.cactus.logcactus.presentation.logcat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.android.tools.idea.logcat.standalone.core.LogEntry

data class LevelColors(val message: Color, val badgeBg: Color, val badgeFg: Color)

class LogRenderCache(
  private val signatureProvider: () -> String,
  // Второй параметр — тег предыдущей (по позиции в списке) строки, для
  // hideDuplicates (TagFormat.hideDuplicates в реальном Logcat).
  private val lineFormatter: (LogEntry, String?) -> AnnotatedString,
  private val lineColorProvider: (String) -> LevelColors
) {
  private var lastRenderedRows: List<LogEntry> = emptyList()
  private var lastRenderedLines: List<RenderedLine> = emptyList()
  private var lastRenderSignature: String = ""

  fun invalidate() {
    lastRenderedRows = emptyList()
    lastRenderedLines = emptyList()
    lastRenderSignature = ""
  }

  fun render(rows: List<LogEntry>): List<RenderedLine> {
    val signature = signatureProvider()
    if (signature != lastRenderSignature) {
      val rebuilt = renderAll(rows)
      lastRenderedRows = rows
      lastRenderedLines = rebuilt
      lastRenderSignature = signature
      return rebuilt
    }

    val previousRows = lastRenderedRows
    val previousLines = lastRenderedLines
    val prevSize = previousRows.size

    if (rows.size >= prevSize && isSamePrefix(previousRows, rows, prevSize)) {
      if (rows.size == prevSize) return previousLines
      val appended = ArrayList<RenderedLine>(rows.size)
      appended.addAll(previousLines)
      var previousTag = previousRows.lastOrNull()?.tag
      for (i in prevSize until rows.size) {
        val entry = rows[i]
        appended.add(toRenderedLine(entry, previousTag))
        previousTag = entry.tag
      }
      lastRenderedRows = rows
      lastRenderedLines = appended
      return appended
    }

    // Буфер заполнен: самые старые строки выпадают из начала, новые добавляются в конец.
    // Без этого случая isSamePrefix всегда падал и на каждый кадр форматировались заново
    // все строки окна (главный источник аллокаций и GC).
    val shift = findSlideOffset(previousRows, rows)
    if (shift > 0) {
      val kept = prevSize - shift
      val slid = ArrayList<RenderedLine>(rows.size)
      slid.addAll(previousLines.subList(shift, prevSize))
      // Первая строка окна лишилась предшественника — для hideDuplicates её тег считается заново.
      if (slid.isNotEmpty()) slid[0] = toRenderedLine(rows[0], null)
      var previousTag = rows[kept - 1].tag
      for (i in kept until rows.size) {
        val entry = rows[i]
        slid.add(toRenderedLine(entry, previousTag))
        previousTag = entry.tag
      }
      lastRenderedRows = rows
      lastRenderedLines = slid
      return slid
    }

    val rebuilt = renderAll(rows)
    lastRenderedRows = rows
    lastRenderedLines = rebuilt
    return rebuilt
  }

  // Возвращает k > 0, если current == previous[k..] + (возможные новые строки), иначе 0.
  private fun findSlideOffset(previous: List<LogEntry>, current: List<LogEntry>): Int {
    if (current.isEmpty() || previous.isEmpty()) return 0
    val first = current[0]
    for (k in 1 until previous.size) {
      if (previous[k] !== first) continue
      val overlap = previous.size - k
      if (current.size < overlap) continue
      var same = true
      for (i in 0 until overlap) {
        if (previous[k + i] !== current[i]) { same = false; break }
      }
      if (same) return k
    }
    return 0
  }

  private fun renderAll(rows: List<LogEntry>): List<RenderedLine> {
    val out = ArrayList<RenderedLine>(rows.size)
    var previousTag: String? = null
    for (entry in rows) {
      out.add(toRenderedLine(entry, previousTag))
      previousTag = entry.tag
    }
    return out
  }

  private fun toRenderedLine(entry: LogEntry, previousTag: String?): RenderedLine {
    val colors = lineColorProvider(entry.level)
    return RenderedLine(lineFormatter(entry, previousTag), entry.message, colors.message, entry.level, colors.badgeBg, colors.badgeFg, entry)
  }

  private fun isSamePrefix(previous: List<LogEntry>, current: List<LogEntry>, count: Int): Boolean {
    for (i in 0 until count) {
      if (previous[i] !== current[i]) return false
    }
    return true
  }
}
