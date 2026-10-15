package com.cactus.logcactus.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.android.tools.idea.logcat.standalone.core.LogLevel
import com.cactus.logcactus.plugin.LogcatSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** Цвета, нужные для отрисовки строк, собранные один раз на композицию. */
class Palette(val levels: Map<LogLevel, LevelColors>, val dim: Color, val dark: Boolean, val accent: Color)

@Composable
fun rememberPalette(): Palette {
  val levels = LogLevel.entries.associateWith { levelColors(it) }
  return Palette(levels, JewelTheme.globalColors.text.info, JewelTheme.isDark, JewelTheme.globalColors.outlines.focused)
}

private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun timestampText(ts: Long, format: String): String = when (format) {
  "EPOCH" -> ts.toString()
  "DATE_TIME" -> DATE_TIME.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
  else -> TIME.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
}

private fun fit(text: String, width: Int): String = if (text.length > width) text.take(width - 1) + "…" else text.padEnd(width)

/**
 * Весь видимый текст строки одной аннотированной строкой: так смещения символов совпадают у отрисовки, выделения мышью
 * и копирования. Раскраска — стилями.
 */
fun rowDisplay(row: Row, session: LogcatSession, hideTag: Boolean, p: Palette): AnnotatedString {
  val entry = row.entry
  val level = LogLevel.fromString(entry.level)
  val colors = p.levels.getValue(level)
  return buildAnnotatedString {
    when (row) {
      is Row.Stack -> withStyle(SpanStyle(color = p.dim)) { append("${row.hidden.size} stack frames — ${row.hidden.first().message.trim()}") }
      is Row.Detail -> withStyle(SpanStyle(color = colors.messageFg)) { append("      "); append(row.text) }
      is Row.Entry -> {
        if (session.showTimestamp) withStyle(SpanStyle(color = p.dim)) { append(timestampText(entry.timestamp, session.timestampFormat)); append("  ") }
        if (session.showPid) withStyle(SpanStyle(color = p.dim)) { append(entry.pid.toString().padEnd(6)); append(' ') }
        if (session.showTag) withStyle(SpanStyle(color = tagColor(entry.tag, p.dark))) { append(fit(if (hideTag) "" else entry.tag, session.tagWidth)); append(' ') }
        if (session.showPackage) withStyle(SpanStyle(color = p.dim)) { append(fit(entry.packageName, session.packageWidth)); append(' ') }
        if (session.showLevel) {
          withStyle(SpanStyle(color = colors.badgeFg, background = colors.badgeBg, fontWeight = FontWeight.Bold)) { append(" ${level.shortName} ") }
          append(' ')
        }
        withStyle(SpanStyle(color = colors.messageFg)) { append(row.headText ?: entry.message) }
      }
    }
  }
}

/** Выделение мышью: якорь и текущий конец (строка списка + смещение символа в [rowDisplay]). */
class Selection(val aRow: Int, val aOff: Int, val fRow: Int, val fOff: Int) {
  private val forward = aRow < fRow || (aRow == fRow && aOff <= fOff)
  val startRow get() = if (forward) aRow else fRow
  val startOff get() = if (forward) aOff else fOff
  val endRow get() = if (forward) fRow else aRow
  val endOff get() = if (forward) fOff else aOff
  val isEmpty get() = aRow == fRow && aOff == fOff

  /** Диапазон символов, выделенный в строке [row] длиной [len]; null — строка вне выделения. */
  fun rangeIn(row: Int, len: Int): IntRange? {
    if (row < startRow || row > endRow) return null
    val from = if (row == startRow) startOff.coerceIn(0, len) else 0
    val to = if (row == endRow) endOff.coerceIn(0, len) else len
    return if (from < to) from until to else if (startRow != endRow && row in (startRow + 1) until endRow) 0 until 0 else null
  }
}

/** Выделенный текст: куски строк, соединённые переводами строк; свёрнутая серия кадров целиком разворачивается в кадры. */
fun selectedText(rows: List<Row>, texts: (Int) -> String, sel: Selection): String {
  val out = ArrayList<String>()
  for (i in sel.startRow..minOf(sel.endRow, rows.lastIndex)) {
    val t = texts(i)
    val r = sel.rangeIn(i, t.length) ?: continue
    val row = rows[i]
    if (row is Row.Stack && !row.expanded && r.first == 0 && r.last + 1 == t.length && r.last >= 0) {
      row.hidden.forEach { out.add(it.message) }
    } else if (r.isEmpty()) out.add("") else out.add(t.substring(r.first, r.last + 1))
  }
  return out.joinToString("\n")
}
