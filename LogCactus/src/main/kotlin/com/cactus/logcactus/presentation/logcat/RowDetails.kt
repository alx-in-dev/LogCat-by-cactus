package com.cactus.logcactus.presentation.logcat

/** Разбор "умных" сообщений: JSON (в том числе после префикса вроде `Body: ` и обрезанный), `Intent { … }` и длинные URL. */
object RowDetails {
  private const val MAX_LENGTH = 20_000
  private const val MAX_PREFIX = 40
  private const val MIN_PAYLOAD = 12
  // Защита от патологических входов: «[[[[…» на 20 000 скобок давало бы отступы в сотни мегабайт.
  private const val MAX_DEPTH = 64
  private const val MAX_OUTPUT = 200_000
  private val INTENT = Regex("""Intent\s*\{(.*)}""")
  private val INTENT_PART = Regex("""(\w+)=(\S+)|\(([^)]*)\)""")

  /** Раскрытие: [start] — индекс начала полезной части в исходном сообщении, [lines] — её читаемое представление. */
  class Expansion(val start: Int, val lines: List<String>, val url: Boolean = false, val json: Boolean = false)

  // Дёшево отсекает почти все строки без полного разбора (вызывается для каждой строки на каждом кадре).
  fun mayBeExpandable(message: String): Boolean {
    if (message.length > MAX_LENGTH || message.length < MIN_PAYLOAD) return false
    return jsonStart(message) >= 0 || message.contains("Intent {") || message.contains("Intent{") || UrlDetails.mayContainUrl(message)
  }

  fun expansion(message: String): Expansion? {
    if (!mayBeExpandable(message)) return null
    val js = jsonStart(message)
    if (js >= 0) {
      val lines = prettyJson(message.substring(js), lenient = true)
      if (lines != null) return Expansion(js, lines, json = true)
    }
    val ii = message.indexOf("Intent").let { if (it >= 0 && message.indexOf('{', it) >= 0) it else -1 }
    if (ii >= 0) parseIntent(message.substring(ii))?.let { return Expansion(ii, it) }
    // Длинные URL: адрес остаётся в строке, параметры — дочерними строками `ключ = значение`.
    UrlDetails.expansion(message)?.let { return Expansion(it.start, it.lines, url = true) }
    return null
  }

  /** Строки раскрытия без префикса; null — не JSON и не Intent. */
  fun expand(message: String): List<String>? = expansion(message)?.lines

  // Начало JSON в первых символах сообщения: `{"` / `{}` или `[{` / `["` / `[[` (сильные признаки, чтобы не ловить "[1] of").
  private fun jsonStart(message: String): Int {
    val limit = minOf(MAX_PREFIX, message.length - 1)
    for (i in 0..limit) {
      val c = message[i]
      if (c != '{' && c != '[') continue
      var j = i + 1
      while (j < message.length && message[j].isWhitespace()) j++
      val next = message.getOrNull(j) ?: return -1
      val strong = if (c == '{') next == '"' || next == '}' else next == '{' || next == '"' || next == '['
      if (strong && message.length - i >= MIN_PAYLOAD) return i
    }
    return -1
  }

  /**
   * Форматирует JSON с отступами. Строгий режим: null при неверных скобках или обрыве.
   * [lenient]: обрыв в конце (logcat режет длинные строки) не ошибка, в конец добавляется "… (truncated)".
   */
  fun prettyJson(text: String, lenient: Boolean = false): List<String>? {
    val out = StringBuilder()
    val stack = ArrayList<Char>()
    var inString = false
    var escape = false
    var indent = 0
    var rootClosedAt = -1
    fun newline() { out.append('\n'); repeat(indent) { out.append("  ") } }
    var i = 0
    while (i < text.length && rootClosedAt < 0) {
      if (out.length > MAX_OUTPUT) return null
      val c = text[i]
      if (inString) {
        out.append(c)
        if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') inString = false
        i++
        continue
      }
      when (c) {
        '"' -> { inString = true; out.append(c) }
        '{', '[' -> {
          if (stack.size >= MAX_DEPTH) return null
          stack.add(c)
          out.append(c)
          var j = i + 1
          while (j < text.length && text[j].isWhitespace()) j++
          val next = text.getOrNull(j)
          if (next == '}' || next == ']') {
            // Пустой объект/массив остаётся в одну строку.
            if ((c == '{') != (next == '}')) return null
            out.append(next)
            stack.removeAt(stack.lastIndex)
            i = j
            if (stack.isEmpty()) rootClosedAt = i
          } else {
            indent++
            newline()
          }
        }
        '}', ']' -> {
          val open = stack.removeLastOrNull() ?: return null
          if ((open == '{') != (c == '}')) return null
          indent--
          newline()
          out.append(c)
          if (stack.isEmpty()) rootClosedAt = i
        }
        ',' -> { out.append(c); newline() }
        ':' -> out.append(": ")
        else -> if (!c.isWhitespace()) out.append(c)
      }
      i++
    }
    val lines = ArrayList(out.toString().lines())
    if (rootClosedAt >= 0) {
      val trailing = text.substring(rootClosedAt + 1).trim()
      if (trailing.isNotEmpty()) lines.add(trailing)
    } else {
      if (!lenient) return null
      lines.add("… (truncated)")
    }
    return lines
  }

  fun parseIntent(text: String): List<String>? {
    val body = INTENT.find(text)?.groupValues?.get(1) ?: return null
    val parts = INTENT_PART.findAll(body).map { m ->
      if (m.groupValues[1].isNotEmpty()) "${m.groupValues[1]} = ${m.groupValues[2]}" else "(${m.groupValues[3]})"
    }.toList()
    return parts.takeIf { it.isNotEmpty() }
  }
}
