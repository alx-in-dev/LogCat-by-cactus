package com.cactus.logcactus.presentation.logcat

import androidx.compose.ui.text.AnnotatedString
import com.android.tools.idea.logcat.standalone.core.LogEntry

/**
 * Пост-обработка списка отрисованных строк: серии кадров стектрейса сворачиваются в одну
 * строку-заглушку, а строки с раскрытым JSON/Intent получают дочерние строки.
 *
 * Возвращает тот же список (тот же экземпляр), если ничего сворачивать не нужно, — вызывается на
 * каждом кадре, лишних аллокаций при обычном потоке быть не должно.
 */
class StackFolder(private val minRun: Int = MIN_RUN) {
  // Заглушки кэшируются, чтобы у неизменившейся серии сохранялся тот же экземпляр (и ключ в LazyColumn).
  private val placeholders = HashMap<Long, RenderedLine>()

  // isExpanded/anyExpanded — множество строк, где пользователь ПЕРЕВЕРНУЛ состояние по умолчанию:
  // стектрейс по умолчанию свёрнут, JSON/Intent при autoDetails раскрыт (иначе свёрнут).
  fun fold(
    rows: List<RenderedLine>,
    foldStacks: Boolean,
    isExpanded: (LogEntry) -> Boolean,
    anyExpanded: Boolean,
    autoDetails: Boolean = false,
    autoUrls: Boolean = autoDetails
  ): List<RenderedLine> {
    if (!foldStacks && !anyExpanded && !autoDetails && !autoUrls) return rows
    var out: ArrayList<RenderedLine>? = null
    var i = 0
    val n = rows.size
    while (i < n) {
      val row = rows[i]
      if (foldStacks && isFrame(row.message)) {
        var j = i + 1
        while (j < n && isFrame(rows[j].message)) j++
        val len = j - i
        if (len >= minRun) {
          val target = out ?: ArrayList<RenderedLine>(n).also { it.addAll(rows.subList(0, i)); out = it }
          val frames = rows.subList(i, j)
          val expanded = isExpanded(frames[0].entry)
          target.add(placeholder(frames, expanded))
          if (expanded) target.addAll(frames)
          i = j
          continue
        }
      }
      if ((autoDetails || autoUrls || anyExpanded) && row.fold == null && RowDetails.mayBeExpandable(row.message) &&
        ((if (UrlDetails.mayContainUrl(row.message) && RowDetails.expansion(row.message)?.url == true) autoUrls else autoDetails) != isExpanded(row.entry))
      ) {
        val expanded = expandedRows(row)
        if (expanded != null) {
          val target = out ?: ArrayList<RenderedLine>(n).also { it.addAll(rows.subList(0, i)); out = it }
          target.add(expanded.head)
          target.addAll(expanded.details)
          i++
          continue
        }
      }
      out?.add(row)
      i++
    }
    return out ?: rows
  }

  private fun placeholder(frames: List<RenderedLine>, expanded: Boolean): RenderedLine {
    val first = frames[0]
    val id = -(first.id * 1000)
    val cached = placeholders[id]
    val hiddenCount = frames.size
    if (cached != null && cached.fold?.expanded == expanded && cached.fold.hidden.size == hiddenCount) return cached
    if (placeholders.size > MAX_CACHE) placeholders.clear()
    val label = if (expanded) "▾ hide $hiddenCount stack frames" else "▸ $hiddenCount stack frames"
    val line = RenderedLine(
      text = AnnotatedString(indentOf(first) + label),
      message = label,
      color = first.color,
      level = first.level,
      badgeBg = first.badgeBg,
      badgeFg = first.badgeFg,
      entry = first.entry,
      id = id,
      fold = RowFold(RowFold.Kind.STACK, first.entry, expanded, frames.toList())
    )
    placeholders[id] = line
    return line
  }

  private class Expanded(val head: RenderedLine, val details: List<RenderedLine>)

  // Раскрытие считается один раз на экземпляр строки (id уникален для экземпляра), а не на каждый кадр.
  private val expandedCache = HashMap<Long, Expanded?>()

  private fun expandedRows(origin: RenderedLine): Expanded? {
    if (expandedCache.containsKey(origin.id)) return expandedCache[origin.id]
    if (expandedCache.size > MAX_CACHE) expandedCache.clear()
    val exp = RowDetails.expansion(origin.message)
    val result = exp?.let { e ->
      val lines = e.lines.mapIndexed { k, l -> detailRow(origin, k, l) }
      Expanded(headRow(origin, e.start), lines)
    }
    expandedCache[origin.id] = result
    return result
  }

  // В исходной строке остаётся префикс до начала JSON/Intent ("Body:"), остальное показывают дочерние строки.
  private fun headRow(origin: RenderedLine, payloadStart: Int): RenderedLine {
    val text = origin.text
    val cut = text.length - (origin.message.length - payloadStart)
    if (!text.text.endsWith(origin.message) || cut !in 0..text.length) return origin
    return RenderedLine(
      text = text.subSequence(0, cut),
      message = origin.message.substring(0, payloadStart),
      color = origin.color,
      level = origin.level,
      badgeBg = origin.badgeBg,
      badgeFg = origin.badgeFg,
      entry = origin.entry,
      id = origin.id,
      fold = RowFold(RowFold.Kind.HEAD, origin.entry, true, listOf(origin))
    )
  }

  // Дочерние строки выравниваются по колонке сообщения (под "Body:"): отступ = ширина колонок времени/тега/процесса.
  private fun indentOf(row: RenderedLine): String = " ".repeat((row.text.length - row.message.length).coerceIn(0, MAX_INDENT))

  private fun detailRow(origin: RenderedLine, index: Int, line: String): RenderedLine = RenderedLine(
    text = AnnotatedString(indentOf(origin) + line),
    message = line,
    color = origin.color,
    level = origin.level,
    badgeBg = origin.badgeBg,
    badgeFg = origin.badgeFg,
    entry = origin.entry,
    id = -(origin.id * 1000 + index + 1),
    fold = RowFold(RowFold.Kind.DETAIL, origin.entry, true, emptyList())
  )

  companion object {
    const val MIN_RUN = 4
    private const val MAX_CACHE = 2000
    private const val MAX_INDENT = 160

    // "\tat com.a.B.c(B.java:12)" и "... 12 more" — кадры стектрейса. Без regex: вызывается на каждый кадр UI.
    fun isFrame(message: String): Boolean {
      var i = 0
      while (i < message.length && (message[i] == ' ' || message[i] == '\t')) i++
      if (i == message.length) return false
      if (message.startsWith("at ", i)) return message.endsWith(")") || message.endsWith(")\r")
      return message.startsWith("... ", i) && message.endsWith(" more")
    }

    /** Текст для копирования: заглушки разворачиваются обратно в кадры, производные строки пропускаются. */
    fun plainLines(rows: List<RenderedLine>): List<String> {
      val out = ArrayList<String>(rows.size)
      for (row in rows) {
        val fold = row.fold
        when {
          fold == null -> out.add(row.text.text)
          fold.kind == RowFold.Kind.STACK && !fold.expanded -> fold.hidden.forEach { out.add(it.text.text) }
          fold.kind == RowFold.Kind.HEAD -> out.add(fold.hidden[0].text.text)
          else -> Unit
        }
      }
      return out
    }
  }
}
