package com.cactus.logcactus.plugin.ui

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.presentation.logcat.RowDetails
import com.cactus.logcactus.presentation.logcat.StackFolder
import com.cactus.logcactus.presentation.logcat.UrlDetails

/** Строка списка: запись, свёрнутая/развёрнутая серия кадров стека или строка раскрытого JSON/Intent. */
sealed interface Row {
  val entry: LogEntry

  class Entry(override val entry: LogEntry, val expandableJson: Boolean = false, val jsonExpanded: Boolean = false, val headText: String? = null) : Row

  /** Заголовок серии кадров: [hidden] — все кадры серии; пока свёрнута, сами кадры в списке не показываются. */
  class Stack(override val entry: LogEntry, val hidden: List<LogEntry>, val expanded: Boolean) : Row

  class Detail(override val entry: LogEntry, val text: String) : Row
}

private const val MIN_STACK_RUN = 4

/** Ключ раскрытия записи — идентичность объекта (одинаковые по значению строки не должны раскрываться вместе). */
fun key(entry: LogEntry): Int = System.identityHashCode(entry)

/**
 * Строит строки списка. [toggled] — записи, у которых пользователь перевернул состояние по умолчанию:
 * стек по умолчанию свёрнут (при [foldStacks]), JSON — раскрыт при [autoJson], длинные URL — при [autoUrl].
 */
fun buildRows(visible: List<LogEntry>, foldStacks: Boolean, autoJson: Boolean, toggled: Set<Int>, autoUrl: Boolean = true): List<Row> {
  val out = ArrayList<Row>(visible.size + 16)
  var i = 0
  val n = visible.size
  while (i < n) {
    val e = visible[i]
    if (foldStacks && StackFolder.isFrame(e.message)) {
      var j = i + 1
      while (j < n && StackFolder.isFrame(visible[j].message)) j++
      if (j - i >= MIN_STACK_RUN) {
        val expanded = key(e) in toggled
        out.add(Row.Stack(e, visible.subList(i, j), expanded))
        if (expanded) for (k in i until j) out.add(Row.Entry(visible[k]))
        i = j
        continue
      }
    }
    val expansion = if (RowDetails.mayBeExpandable(e.message)) RowDetails.expansion(e.message) else null
    // Общий RowDetails отдаёт и URL; у плагина для них своя настройка автораскрытия и своя укороченная строка.
    val url = if (expansion?.url == true) UrlDetails.expansion(e.message) else null
    val isUrl = url != null
    if (isUrl && url != null) {
      val expanded = autoUrl != (key(e) in toggled)
      if (expanded) {
        out.add(Row.Entry(e, expandableJson = true, jsonExpanded = true, headText = url.head))
        url.lines.forEach { out.add(Row.Detail(e, it)) }
      } else out.add(Row.Entry(e, expandableJson = true, jsonExpanded = false))
    } else if (expansion != null) {
      val expanded = autoJson != (key(e) in toggled)
      if (expanded) {
        out.add(Row.Entry(e, expandableJson = true, jsonExpanded = true, headText = e.message.substring(0, expansion.start).trimEnd()))
        expansion.lines.forEach { out.add(Row.Detail(e, it)) }
      } else {
        out.add(Row.Entry(e, expandableJson = true, jsonExpanded = false))
      }
    } else {
      out.add(Row.Entry(e))
    }
    i++
  }
  return out
}

/** Текст строки для копирования: свёрнутая серия разворачивается обратно в кадры, строки JSON пропускаются (их несёт запись). */
fun copyLines(rows: List<Row>): List<LogEntry> {
  val out = ArrayList<LogEntry>()
  for (r in rows) when (r) {
    is Row.Entry -> out.add(r.entry)
    is Row.Stack -> if (!r.expanded) out.addAll(r.hidden)
    is Row.Detail -> Unit
  }
  return out
}
