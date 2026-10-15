package com.cactus.logcactus.plugin.ui

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** Роли частей строки фильтра. Границы считаются по тому же синтаксису, что у `LogQueryMatcher` (кавычки, `&&`/`||`, `-`, `~`/`=`). */
enum class FilterRole { KEY, OPERATOR, VALUE, QUOTED, NEGATION, LOGIC, PAREN }

data class FilterSpan(val role: FilterRole, val start: Int, val end: Int)

private val KNOWN_KEYS = setOf("tag", "level", "is", "message", "line", "process", "package", "app", "name", "age")

/** Разбор без исключений: на недописанном тексте (открытая кавычка, лишняя скобка) всё равно возвращает осмысленные куски. */
fun highlightFilter(text: String): List<FilterSpan> {
  val out = mutableListOf<FilterSpan>()
  var i = 0
  val n = text.length
  while (i < n) {
    val ch = text[i]
    when {
      ch.isWhitespace() -> i++
      ch == '(' || ch == ')' -> { out += FilterSpan(FilterRole.PAREN, i, i + 1); i++ }
      ch == '!' -> { out += FilterSpan(FilterRole.LOGIC, i, i + 1); i++ }
      (ch == '&' || ch == '|') -> {
        val len = if (i + 1 < n && text[i + 1] == ch) 2 else 1
        out += FilterSpan(FilterRole.LOGIC, i, i + len); i += len
      }
      else -> i = lexWord(text, i, out)
    }
  }
  return out
}

/** Слово до пробела или символа логики; кавычки внутри слова скрывают разделители. Возвращает позицию после слова. */
private fun lexWord(text: String, from: Int, out: MutableList<FilterSpan>): Int {
  val n = text.length
  var i = from
  var neg = -1
  if (text[i] == '-' && i + 1 < n && !text[i + 1].isWhitespace()) { neg = i; i++ }
  val wordStart = i
  var colon = -1
  var inQuote = false
  while (i < n) {
    val c = text[i]
    if (inQuote) { if (c == '"') inQuote = false; i++; continue }
    if (c == '"') { inQuote = true; i++; continue }
    if (c.isWhitespace() || c == '&' || c == '|' || c == '(' || c == ')' || c == '!') break
    if (c == ':' && colon < 0) colon = i
    i++
  }
  if (neg >= 0) out += FilterSpan(FilterRole.NEGATION, neg, neg + 1)
  val keyPart = if (colon > 0) text.substring(wordStart, colon) else ""
  val known = colon > 0 && keyPart.trimEnd('~', '=').lowercase() in KNOWN_KEYS
  if (!known) {
    // Слово без известного ключа — обычный текст по сообщению; цветом выделяем только кавычки.
    addValue(text, wordStart, i, out)
    return i
  }
  val opLen = keyPart.length - keyPart.trimEnd('~', '=').length
  val keyEnd = colon - opLen
  out += FilterSpan(FilterRole.KEY, wordStart, keyEnd)
  if (opLen > 0) out += FilterSpan(FilterRole.OPERATOR, keyEnd, colon)
  out += FilterSpan(FilterRole.OPERATOR, colon, colon + 1)
  addValue(text, colon + 1, i, out)
  return i
}

private fun addValue(text: String, start: Int, end: Int, out: MutableList<FilterSpan>) {
  // Кавычки красим как строку, остальное — как значение; незакрытая кавычка тянется до конца слова.
  var i = start
  var segStart = start
  var inQuote = false
  while (i < end) {
    if (text[i] == '"') {
      if (!inQuote) {
        if (i > segStart) out += FilterSpan(FilterRole.VALUE, segStart, i)
        segStart = i; inQuote = true
      } else {
        out += FilterSpan(FilterRole.QUOTED, segStart, i + 1)
        segStart = i + 1; inQuote = false
      }
    }
    i++
  }
  if (segStart < end) out += FilterSpan(if (inQuote) FilterRole.QUOTED else FilterRole.VALUE, segStart, end)
}

/** Цвета — из цветовой схемы редактора (ключи DEFAULT_*), с запасными. */
@Composable
fun filterOutputTransformation(): OutputTransformation {
  val dark = JewelTheme.isDark
  return remember(dark) {
    val scheme = EditorColorsManager.getInstance().globalScheme
    fun color(key: String, fallback: Long): Color =
      scheme.getAttributes(TextAttributesKey.find(key))?.foregroundColor?.let { Color(it.red, it.green, it.blue, it.alpha) }
        ?: Color(fallback)
    val keyword = color("DEFAULT_KEYWORD", if (dark) 0xFFCF8E6D else 0xFF0033B3)
    val string = color("DEFAULT_STRING", if (dark) 0xFF6AAB73 else 0xFF067D17)
    val operation = color("DEFAULT_OPERATION_SIGN", if (dark) 0xFFBCBEC4 else 0xFF000000)
    val number = color("DEFAULT_NUMBER", if (dark) 0xFF2AACB8 else 0xFF1750EB)
    val styles = mapOf(
      FilterRole.KEY to SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold),
      FilterRole.OPERATOR to SpanStyle(color = operation),
      FilterRole.VALUE to SpanStyle(color = number),
      FilterRole.QUOTED to SpanStyle(color = string),
      FilterRole.NEGATION to SpanStyle(color = keyword),
      FilterRole.LOGIC to SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold),
      FilterRole.PAREN to SpanStyle(color = operation)
    )
    OutputTransformation {
      highlightFilter(asCharSequence().toString()).forEach { s ->
        styles[s.role]?.let { addStyle(it, s.start, s.end) }
      }
    }
  }
}
