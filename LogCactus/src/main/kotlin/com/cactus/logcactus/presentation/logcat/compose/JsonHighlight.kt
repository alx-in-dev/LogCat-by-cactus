package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Цвета раскладки JSON в логе: те же, что у редактора IDE (Darcula / Light). */
class JsonColors(val key: Color, val string: Color, val number: Color, val keyword: Color) {
  companion object {
    val DARK = JsonColors(Color(0xFF9876AA), Color(0xFF6A8759), Color(0xFF6897BB), Color(0xFFCC7832))
    val LIGHT = JsonColors(Color(0xFF871094), Color(0xFF067D17), Color(0xFF1750EB), Color(0xFF0033B3))
  }
}

/**
 * Подсветка одной строки уже отформатированного JSON (RowDetails.prettyJson кладёт по токену на строку):
 * ключи, строки, числа и true/false/null; скобки и запятые остаются цветом сообщения. [prefix] — отступ под колонки.
 * [charSp] > 0 — включён перенос строк: продолжение длинной строки сдвигается под её значение (висячий отступ),
 * а не прижимается к левому краю окна.
 */
object JsonHighlight {
  private val NUMBER = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

  fun line(prefix: String, line: String, colors: JsonColors, charSp: Float = 0f): AnnotatedString = buildAnnotatedString {
    if (charSp > 0f) {
      val hang = (prefix.length + line.takeWhile { it == ' ' }.length + 2) * charSp
      pushStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = hang.sp)))
    }
    append(prefix)
    var i = 0
    while (i < line.length) {
      val c = line[i]
      when {
        c == '"' -> {
          var j = i + 1
          while (j < line.length && line[j] != '"') j += if (line[j] == '\\') 2 else 1
          val end = (j + 1).coerceAtMost(line.length)
          var k = end
          while (k < line.length && line[k] == ' ') k++
          val isKey = k < line.length && line[k] == ':'
          withStyle(SpanStyle(color = if (isKey) colors.key else colors.string)) { append(line, i, end) }
          i = end
        }
        c == '-' || c.isDigit() -> {
          val m = NUMBER.matchAt(line, i)
          if (m != null) { withStyle(SpanStyle(color = colors.number)) { append(m.value) }; i += m.value.length }
          else { append(c); i++ }
        }
        line.startsWith("true", i) || line.startsWith("false", i) || line.startsWith("null", i) -> {
          val n = if (line.startsWith("false", i)) 5 else 4
          withStyle(SpanStyle(color = colors.keyword)) { append(line, i, i + n) }
          i += n
        }
        else -> { append(c); i++ }
      }
    }
  }
}