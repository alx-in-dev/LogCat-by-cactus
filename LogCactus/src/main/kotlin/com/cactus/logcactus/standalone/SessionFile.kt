package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.LogEntry
import java.io.File

/**
 * Файл сессии `.logcactus`: текст в UTF-8. Первая строка — сигнатура, затем `#ключ=значение`
 * (метаданные), затем по строке на запись: `время\tуровень\tтег\tpid\tпроцесс\tпакет\tсообщение`.
 * Табуляция, перевод строки и `\` внутри полей экранируются.
 */
object SessionFile {
  const val EXTENSION = "logcactus"
  /** Ключ в meta: сколько ранних записей не поместилось при чтении (UI может предупредить пользователя). */
  const val TRUNCATED_KEY = "truncatedEntries"
  private const val MAGIC = "#logcactus-session 1"
  private const val MAX_ENTRIES = 100_000

  class Session(val meta: Map<String, String>, val entries: List<LogEntry>)

  fun write(file: File, meta: Map<String, String>, entries: List<LogEntry>) {
    AtomicFiles.write(file.toPath()) { out -> out.bufferedWriter(Charsets.UTF_8).let { w ->
      w.write(MAGIC); w.newLine()
      for ((k, v) in meta) if (v.isNotBlank()) { w.write("#${k.filter { it.isLetterOrDigit() || it == '_' }}=${escape(v)}"); w.newLine() }
      for (e in entries) {
        w.write(
          listOf(e.timestamp.toString(), e.level, e.tag, e.pid.toString(), e.processName, e.packageName, e.message)
            .joinToString("\t") { escape(it) }
        )
        w.newLine()
      }
      w.flush()
    } }
  }

  /** Читает сессию; null — файл не похож на сессию LogCactus. Записей не больше [MAX_ENTRIES] (берутся последние). */
  fun read(file: File): Session? {
    val meta = LinkedHashMap<String, String>()
    // Держим только последние MAX_ENTRIES: огромный файл не раздувает память, а усечение помечается в meta.
    val entries = java.util.ArrayDeque<LogEntry>()
    var dropped = 0
    file.bufferedReader(Charsets.UTF_8).use { r ->
      if (r.readLine() != MAGIC) return null
      for (line in r.lineSequence()) {
        if (line.startsWith("#")) {
          val eq = line.indexOf('=')
          if (eq > 1) meta[line.substring(1, eq)] = unescape(line.substring(eq + 1))
          continue
        }
        parseEntry(line)?.let { entries.addLast(it); if (entries.size > MAX_ENTRIES) { entries.removeFirst(); dropped++ } }
      }
    }
    if (dropped > 0) meta[TRUNCATED_KEY] = dropped.toString()
    return Session(meta, entries.toList())
  }

  private fun parseEntry(line: String): LogEntry? {
    val f = line.split('\t')
    if (f.size < 7) return null
    val ts = f[0].toLongOrNull() ?: return null
    val pid = f[3].toIntOrNull() ?: return null
    return LogEntry(ts, f[1], unescape(f[2]), pid, unescape(f[4]), unescape(f[5]), unescape(f.subList(6, f.size).joinToString("\t")))
  }

  internal fun escape(s: String): String {
    if (s.none { it == '\\' || it == '\t' || it == '\n' || it == '\r' }) return s
    val sb = StringBuilder(s.length + 8)
    for (c in s) when (c) {
      '\\' -> sb.append("\\\\")
      '\t' -> sb.append("\\t")
      '\n' -> sb.append("\\n")
      '\r' -> sb.append("\\r")
      else -> sb.append(c)
    }
    return sb.toString()
  }

  internal fun unescape(s: String): String {
    if (s.indexOf('\\') < 0) return s
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c == '\\' && i + 1 < s.length) {
        when (s[i + 1]) {
          't' -> sb.append('\t')
          'n' -> sb.append('\n')
          'r' -> sb.append('\r')
          '\\' -> sb.append('\\')
          else -> sb.append(c).append(s[i + 1])
        }
        i += 2
      } else {
        sb.append(c)
        i++
      }
    }
    return sb.toString()
  }
}
