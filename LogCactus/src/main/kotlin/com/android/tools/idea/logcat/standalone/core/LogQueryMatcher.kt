package com.android.tools.idea.logcat.standalone.core

import java.time.Instant
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * [minePackage] — приложение, которое подставляется вместо `package:mine` / `app:mine`
 * (в LogCactus это приложение на экране устройства; в Studio был бы пакет проекта).
 * Пустое значение — `mine` не разрешён (см. [Term.matches]).
 */
class LogQueryMatcher(query: String, regex: Boolean, caseSensitive: Boolean, minePackage: String = "") {
  private val root: Node = parseExpression(resolveMine(query, minePackage), regex, caseSensitive)

  fun matches(entry: LogEntry): Boolean = root.matches(entry)

  private fun interface Node {
    fun matches(entry: LogEntry): Boolean
  }

  private data class Token(val type: TokenType, val text: String)

  private enum class TokenType {
    WORD, SYMBOL
  }

  private class TermNode(val term: Term) : Node {
    override fun matches(entry: LogEntry): Boolean = term.matches(entry)
  }

  private class NotNode(private val child: Node) : Node {
    override fun matches(entry: LogEntry): Boolean = !child.matches(entry)
  }

  private class AndNode(private val children: List<Node>) : Node {
    override fun matches(entry: LogEntry): Boolean = children.all { it.matches(entry) }
  }

  private class OrNode(private val children: List<Node>) : Node {
    override fun matches(entry: LogEntry): Boolean = children.any { it.matches(entry) }
  }

  private class Parser(
    private val tokens: List<Token>,
    private val regex: Boolean,
    private val caseSensitive: Boolean
  ) {
    private var pos = 0

    fun isAtEnd(): Boolean = pos >= tokens.size

    fun parseExpression(): Node = parseOr()

    private fun parseOr(): Node {
      val parts = mutableListOf<Node>()
      parts += parseAnd()
      while (matchSymbol("|")) parts += parseAnd()
      return if (parts.size == 1) parts[0] else OrNode(parts)
    }

    private fun parseAnd(): Node {
      val parts = mutableListOf<Node>()
      var explicit = false
      parts += parseUnary()
      while (!isAtEnd()) {
        if (peekSymbol(")") || peekSymbol("|")) break
        if (matchSymbol("&")) explicit = true
        parts += parseUnary()
      }
      // Одинаковые ключи без явного «&» объединяются по ИЛИ (tag:a tag:b); написанный явно «&» — это И.
      return if (parts.size == 1) parts[0] else AndNode(if (explicit) parts else collapseSameKeyTerms(parts))
    }

    private fun parseUnary(): Node = if (matchSymbol("!")) NotNode(parseUnary()) else parsePrimary()

    private fun parsePrimary(): Node {
      if (matchSymbol("(")) {
        val inside = parseExpression()
        consumeSymbol(")")
        return inside
      }
      val token = consumeWord()
      return buildNodeFromWord(token.text, regex, caseSensitive)
    }

    private fun matchSymbol(symbol: String): Boolean {
      if (peekSymbol(symbol)) {
        pos++
        return true
      }
      return false
    }

    private fun peekSymbol(symbol: String): Boolean {
      return !isAtEnd() && tokens[pos].type == TokenType.SYMBOL && tokens[pos].text == symbol
    }

    private fun consumeSymbol(symbol: String) {
      if (!matchSymbol(symbol)) throw IllegalStateException("Expected symbol: $symbol")
    }

    private fun consumeWord(): Token {
      if (isAtEnd() || tokens[pos].type != TokenType.WORD) throw IllegalStateException("Expected filter term")
      return tokens[pos++]
    }
  }

  private class Term(
    private val key: String,
    private val value: String,
    regex: Boolean,
    private val caseSensitive: Boolean,
    forceRegex: Boolean,
    private val exact: Boolean = false
  ) {
    private val regex: Boolean = !exact && (forceRegex || regex)
    private val compiled: Pattern? = compile(value, this.regex, caseSensitive)

    fun matches(entry: LogEntry): Boolean {
      return when (key) {
        "tag" -> match(entry.tag)
        "level" -> matchLevel(entry)
        "is" -> matchIs(entry)
        "message", "line" -> match(entry.message)
        "process" -> match(if (entry.processName.isEmpty()) entry.pid.toString() else entry.processName)
        // "app:" — собственный алиас LogCactus для "package:" (в оригинальном
        // Logcat такого ключа нет), нужен для пикера приложений устройства с
        // иконками; семантика фильтрации идентична "package:".
        "package", "app" -> {
          if (value.equals("mine", ignoreCase = true)) {
            // Сюда попадаем только если пакет не известен (иначе mine уже переписан в
            // "package=:<pkg>" в resolveMine): пока приложение на экране не определено,
            // фильтр не режет поток, чтобы пользователь не смотрел на пустой список.
            val mine = System.getProperty("logcactus.package", "")
            mine.isEmpty() || matchesExact(mine, entry.packageName)
          } else {
            match(entry.packageName)
          }
        }
        "name" -> match(entry.packageName)
        "age" -> matchAge(entry)
        else -> match(entry.message)
      }
    }

    fun keyForMerge(): String = key

    // level:W — это «WARN и выше» (как в фильтре уровня), а не подстрока в слове уровня.
    private fun matchLevel(entry: LogEntry): Boolean {
      val wanted = levelRank(value) ?: return match(entry.level)
      val actual = levelRank(entry.level) ?: return false
      return actual >= wanted
    }

    private fun levelRank(text: String): Int? {
      val t = text.trim().uppercase(Locale.ROOT)
      if (t.isEmpty()) return null
      return LEVELS.indexOfFirst { it.startsWith(t) }.takeIf { it >= 0 }
    }

    private fun matchIs(entry: LogEntry): Boolean {
      val v = value.lowercase(Locale.ROOT)
      if (v == "crash") return entry.level == "ERROR" && entry.message.contains("FATAL EXCEPTION")
      if (v == "stacktrace") return entry.message.contains("\tat ")
      if (v == "firebase") {
        return entry.tag.contains("firebase", ignoreCase = true) ||
          entry.packageName.contains("firebase", ignoreCase = true) ||
          entry.message.contains("firebase", ignoreCase = true)
      }
      return match(entry.level)
    }

    private fun matchAge(entry: LogEntry): Boolean {
      val maxAgeMs = parseAgeToMillis(value)
      if (maxAgeMs < 0) return true
      val ageMs = (Instant.now().toEpochMilli() - entry.timestamp).coerceAtLeast(0L)
      return ageMs <= maxAgeMs
    }

    private fun match(input: String): Boolean {
      if (value.isEmpty()) return true
      if (exact) return matchesExact(value, input)
      if (!regex) {
        return if (caseSensitive) input.contains(value) else input.lowercase(Locale.ROOT).contains(value.lowercase(Locale.ROOT))
      }
      // Невалидный regex (пользователь ещё печатает) — ищем как обычный текст, а не «ничего не найдено».
      val c = compiled ?: return if (caseSensitive) input.contains(value) else input.lowercase(Locale.ROOT).contains(value.lowercase(Locale.ROOT))
      return c.matcher(input).find()
    }

    private fun matchesExact(expected: String, actual: String): Boolean {
      return if (caseSensitive) expected == actual else expected.equals(actual, ignoreCase = true)
    }

    companion object {
      private val LEVELS = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT")

      private fun compile(value: String, regex: Boolean, caseSensitive: Boolean): Pattern? {
        if (!regex || value.isEmpty()) return null
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return try {
          Pattern.compile(value, flags)
        } catch (_: PatternSyntaxException) {
          null
        }
      }

      private fun parseAgeToMillis(raw: String): Long {
        var value = raw.trim().lowercase(Locale.ROOT)
        if (value.isEmpty()) return -1
        var multiplier = 1000L
        val suffix = value.last()
        if (suffix == 's' || suffix == 'm' || suffix == 'h' || suffix == 'd') {
          value = value.dropLast(1)
          multiplier = when (suffix) {
            'm' -> 60_000L
            'h' -> 3_600_000L
            'd' -> 86_400_000L
            else -> 1000L
          }
        }
        val amount = value.toLongOrNull() ?: return -1
        return amount.coerceAtLeast(0L) * multiplier
      }
    }
  }

  companion object {
    // Слово с неизвестным ключом (http://x, key:value из текста) — обычный текст, а не пустой фильтр.
    private val KNOWN_KEYS = setOf("tag", "level", "is", "message", "line", "process", "package", "app", "name", "age")
    private val TRUE_NODE = Node { true }
    private val MINE_TERM = Regex("""\b(package|app):mine(?![\w.])""", RegexOption.IGNORE_CASE)

    // "package:mine" -> "package=:com.pkg" (точное сравнение, ключ "=" — уже существующий синтаксис).
    private fun resolveMine(query: String, minePackage: String): String =
      if (minePackage.isBlank()) query else MINE_TERM.replace(query) { "package=:$minePackage" }

    private fun parseExpression(query: String, regex: Boolean, caseSensitive: Boolean): Node {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) return TRUE_NODE

      val tokens = tokenize(trimmed)
      if (tokens.isEmpty()) return TRUE_NODE

      return try {
        val parser = Parser(tokens, regex, caseSensitive)
        val node = parser.parseExpression()
        if (!parser.isAtEnd()) fallbackAnd(tokens, regex, caseSensitive) else node
      } catch (_: RuntimeException) {
        fallbackAnd(tokens, regex, caseSensitive)
      }
    }

    private fun tokenize(query: String): List<Token> {
      val out = mutableListOf<Token>()
      val buf = StringBuilder()
      var inQuote = false

      var i = 0
      while (i < query.length) {
        val ch = query[i]
        if (inQuote) {
          if (ch == '"') {
            inQuote = false
          } else {
            buf.append(ch)
          }
          i++
          continue
        }

        if (ch == '"') {
          inQuote = true
          i++
          continue
        }
        if (ch.isWhitespace()) {
          flushWord(buf, out)
          i++
          continue
        }
        if (ch == '&' && i + 1 < query.length && query[i + 1] == '&') {
          flushWord(buf, out)
          out += Token(TokenType.SYMBOL, "&")
          i += 2
          continue
        }
        if (ch == '|' && i + 1 < query.length && query[i + 1] == '|') {
          flushWord(buf, out)
          out += Token(TokenType.SYMBOL, "|")
          i += 2
          continue
        }
        if (ch == '&' || ch == '|' || ch == '(' || ch == ')' || ch == '!') {
          flushWord(buf, out)
          out += Token(TokenType.SYMBOL, ch.toString())
          i++
          continue
        }
        buf.append(ch)
        i++
      }

      flushWord(buf, out)
      return out
    }

    private fun flushWord(buf: StringBuilder, out: MutableList<Token>) {
      if (buf.isEmpty()) return
      out += Token(TokenType.WORD, buf.toString())
      buf.setLength(0)
    }

    private fun fallbackAnd(tokens: List<Token>, regex: Boolean, caseSensitive: Boolean): Node {
      val terms = tokens
        .filter { it.type == TokenType.WORD }
        .map { buildNodeFromWord(it.text, regex, caseSensitive) }
      if (terms.isEmpty()) return TRUE_NODE
      return AndNode(terms)
    }

    private fun buildNodeFromWord(raw: String, regex: Boolean, caseSensitive: Boolean): Node {
      if (raw.startsWith("-") && raw.length > 1) {
        return NotNode(TermNode(buildTerm(raw.substring(1), regex, caseSensitive)))
      }
      return TermNode(buildTerm(raw, regex, caseSensitive))
    }

    private fun buildTerm(raw: String, regex: Boolean, caseSensitive: Boolean): Term {
      val colon = raw.indexOf(':')
      if (colon > 0 && raw.substring(0, colon).trimEnd('~', '=').lowercase(Locale.ROOT) in KNOWN_KEYS) {
        var key = raw.substring(0, colon).lowercase(Locale.ROOT)
        val value = raw.substring(colon + 1)
        var forceRegex = false
        var forceExact = false
        if (key.endsWith("~")) {
          forceRegex = true
          key = key.dropLast(1)
        } else if (key.endsWith("=")) {
          forceExact = true
          key = key.dropLast(1)
        }
        return Term(key, value, regex, caseSensitive, forceRegex, forceExact)
      }
      return Term("message", raw, regex, caseSensitive, false)
    }

    private fun collapseSameKeyTerms(parts: List<Node>): List<Node> {
      val out = mutableListOf<Node>()
      val firstIndexByKey = linkedMapOf<String, Int>()
      val termsByKey = linkedMapOf<String, MutableList<Node>>()

      for (node in parts) {
        if (node !is TermNode) {
          out += node
          continue
        }
        val key = node.term.keyForMerge()
        val bucket = termsByKey.getOrPut(key) { mutableListOf() }
        bucket += node
        if (firstIndexByKey[key] == null) {
          firstIndexByKey[key] = out.size
          out += node
        }
      }

      for ((key, bucket) in termsByKey) {
        if (bucket.size < 2) continue
        val index = firstIndexByKey[key] ?: continue
        out[index] = OrNode(bucket)
      }
      return out
    }
  }
}
