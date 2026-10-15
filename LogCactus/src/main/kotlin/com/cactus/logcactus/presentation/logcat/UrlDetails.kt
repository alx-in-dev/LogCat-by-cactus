package com.cactus.logcactus.presentation.logcat

import java.net.URLDecoder

/**
 * Разбор длинных URL в сообщении: адрес без запроса остаётся в строке, параметры раскрываются столбиком
 * `ключ = значение` (значения декодируются из %XX), чтобы длинную ссылку можно было прочитать.
 */
object UrlDetails {
  /** [head] — сообщение с укороченными ссылками; [start] — позиция `?` первой раскрытой ссылки (всё после неё несут [lines]). */
  class Expansion(val head: String, val lines: List<String>, val start: Int)

  private const val MIN_LENGTH = 120
  private const val MAX_PARAMS = 300

  fun mayContainUrl(message: String): Boolean = message.length >= MIN_LENGTH && message.contains("://") && message.contains('?')

  fun expansion(message: String): Expansion? {
    if (!mayContainUrl(message)) return null
    val head = StringBuilder()
    val lines = ArrayList<String>()
    var i = 0
    var found = false
    var firstStart = -1
    while (i < message.length) {
      val start = nextUrlStart(message, i)
      if (start < 0) { head.append(message, i, message.length); break }
      val end = urlEnd(message, start)
      val url = message.substring(start, end)
      val q = url.indexOf('?')
      head.append(message, i, start)
      if (url.length >= MIN_LENGTH && q > 0 && q < url.length - 1) {
        found = true
        if (firstStart < 0) firstStart = start + q
        val base = url.substring(0, q)
        head.append(base).append("?…")
        lines.add(base)
        url.substring(q + 1).split('&').filter { it.isNotEmpty() }.take(MAX_PARAMS).forEach { param ->
          val eq = param.indexOf('=')
          val key = decode(if (eq < 0) param else param.substring(0, eq))
          val value = if (eq < 0) "" else decode(param.substring(eq + 1))
          lines.add("  $key = $value")
        }
      } else head.append(url)
      i = end
    }
    return if (found) Expansion(head.toString(), lines, firstStart) else null
  }

  private fun nextUrlStart(s: String, from: Int): Int {
    var idx = s.indexOf("://", from)
    while (idx >= 0) {
      var b = idx
      while (b > from && s[b - 1].isLetter()) b--
      val scheme = s.substring(b, idx)
      if (scheme.equals("http", true) || scheme.equals("https", true)) return b
      idx = s.indexOf("://", idx + 3)
    }
    return -1
  }

  /** Конец ссылки: пробел/кавычка/угловая скобка; квадратные скобки внутри допустимы, лишняя закрывающая — конец. */
  private fun urlEnd(s: String, start: Int): Int {
    var depth = 0
    var i = start
    while (i < s.length) {
      val c = s[i]
      if (c.isWhitespace() || c == '"' || c == '\'' || c == '<' || c == '>') break
      if (c == '[') depth++
      if (c == ']') { if (depth == 0) break else depth-- }
      i++
    }
    return i
  }

  private fun decode(text: String): String = runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrDefault(text)
}
