package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import java.util.IdentityHashMap

/**
 * Строка Canvas-списка поверх записей лога (ПРОТОТИП, см. LogCanvasList):
 * ENTRY — обычная запись; STACK — заглушка серии кадров стектрейса (hidden — сами кадры, expanded — кадры показаны ниже);
 * HEAD — запись с раскрытым JSON/Intent, в которой остаётся префикс до начала полезной части (payloadStart);
 * DETAIL — дочерняя строка раскрытия JSON/Intent (line).
 * Объекты стабильны между обновлениями списка (кэш в [CanvasFolder]), поэтому кэши раскладки в Canvas по ним живут.
 */
class CanvasItem(
  val entry: LogEntry,
  val kind: Kind,
  val expanded: Boolean = false,
  val hidden: List<LogEntry> = emptyList(),
  val payloadStart: Int = -1,
  val line: String = ""
) {
  enum class Kind { ENTRY, STACK, HEAD, DETAIL }

  /** Последняя запись лога, которую покрывает строка (для заглушки стека — последний кадр). */
  val lastEntry: LogEntry get() = if (kind == Kind.STACK && hidden.isNotEmpty()) hidden.last() else entry

  /** Строка покрывает запись [e] (для свёрнутой заглушки — среди скрытых кадров). */
  fun covers(e: LogEntry): Boolean = entry === e || (kind == Kind.STACK && !expanded && hidden.any { it === e })
}

/**
 * Превращает отфильтрованный список записей в строки Canvas-списка: серии кадров стектрейса (≥ MIN_RUN подряд)
 * сворачиваются в заглушку, JSON/Intent в сообщении раскрываются в дочерние строки. Логика та же, что у
 * [StackFolder] для списка на Compose-узлах, но без RenderedLine: форматирование остаётся ленивым (только видимые).
 */
class CanvasFolder(private val minRun: Int = StackFolder.MIN_RUN) {
  private val entryItems = IdentityHashMap<LogEntry, CanvasItem>()
  private val stackItems = IdentityHashMap<LogEntry, CanvasItem>() // ключ — первый кадр серии
  private val expansions = IdentityHashMap<LogEntry, List<CanvasItem>?>() // head + details; null — раскрывать нечего
  private val urlExpansions = IdentityHashMap<LogEntry, Boolean>() // раскрытие — это длинный URL (своя настройка автораскрытия)

  /**
   * [isToggled] — записи, у которых пользователь ПЕРЕВЕРНУЛ состояние по умолчанию: стектрейс по умолчанию свёрнут,
   * JSON/Intent при [autoDetails] и длинные URL при [autoUrls] раскрыты (иначе свёрнуты).
   */
  fun fold(
    rows: List<LogEntry>,
    foldStacks: Boolean,
    isToggled: (LogEntry) -> Boolean,
    anyToggled: Boolean,
    autoDetails: Boolean,
    autoUrls: Boolean = autoDetails
  ): List<CanvasItem> {
    val limit = rows.size + 2000
    if (entryItems.size > limit || stackItems.size > limit || expansions.size > limit) { entryItems.clear(); stackItems.clear(); expansions.clear(); urlExpansions.clear() }
    val out = ArrayList<CanvasItem>(rows.size + 16)
    val details = autoDetails || autoUrls || anyToggled
    var i = 0
    val n = rows.size
    while (i < n) {
      val e = rows[i]
      if (foldStacks && StackFolder.isFrame(e.message)) {
        var j = i + 1
        while (j < n && StackFolder.isFrame(rows[j].message)) j++
        if (j - i >= minRun) {
          val expanded = isToggled(e)
          out.add(stackItem(e, rows, i, j, expanded))
          if (expanded) for (k in i until j) out.add(entryItem(rows[k]))
          i = j
          continue
        }
      }
      if (details && RowDetails.mayBeExpandable(e.message)) {
        val exp = expansionOf(e)
        val auto = if (urlExpansions[e] == true) autoUrls else autoDetails
        if (exp != null && auto != isToggled(e)) {
          out.addAll(exp)
          i++
          continue
        }
      }
      out.add(entryItem(e))
      i++
    }
    return out
  }

  private fun entryItem(e: LogEntry): CanvasItem = entryItems.getOrPut(e) { CanvasItem(e, CanvasItem.Kind.ENTRY) }

  private fun stackItem(first: LogEntry, rows: List<LogEntry>, from: Int, to: Int, expanded: Boolean): CanvasItem {
    val cached = stackItems[first]
    val count = to - from
    if (cached != null && cached.expanded == expanded && cached.hidden.size == count) return cached
    val item = CanvasItem(first, CanvasItem.Kind.STACK, expanded, ArrayList(rows.subList(from, to)))
    stackItems[first] = item
    return item
  }

  private fun expansionOf(e: LogEntry): List<CanvasItem>? {
    if (expansions.containsKey(e)) return expansions[e]
    val exp = RowDetails.expansion(e.message)
    if (exp?.url == true) urlExpansions[e] = true
    val items = exp?.let {
      buildList {
        add(CanvasItem(e, CanvasItem.Kind.HEAD, expanded = true, payloadStart = it.start))
        it.lines.forEach { l -> add(CanvasItem(e, CanvasItem.Kind.DETAIL, expanded = true, line = l)) }
      }
    }
    expansions[e] = items
    return items
  }
}
