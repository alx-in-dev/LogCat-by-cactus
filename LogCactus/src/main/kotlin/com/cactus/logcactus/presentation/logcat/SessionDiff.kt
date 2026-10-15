package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry

/** Сравнение двух наборов логов (baseline и текущего): что появилось, что пропало, где изменился объём. */
object SessionDiff {
  class Item(val text: String, val count: Int)
  class Report(
    val baselineSize: Int,
    val currentSize: Int,
    val newErrors: List<Item>,
    val goneErrors: List<Item>,
    val newCrashes: List<Item>,
    val goneCrashes: List<Item>,
    val newTags: List<Item>,
    val changedTags: List<String>
  ) {
    val hasDifferences: Boolean
      get() = listOf(newErrors, goneErrors, newCrashes, goneCrashes, newTags, changedTags).any { it.isNotEmpty() }
  }

  private const val MAX_ITEMS = 25
  private val HEX = Regex("""0x[0-9a-fA-F]+""")
  private val UUID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
  private val NUMBER = Regex("""\d+""")

  // Сообщение без "шума" (числа, адреса, uuid): одна и та же ошибка с другим id считается одной.
  fun normalize(message: String): String =
    message.trim().replace(UUID, "#").replace(HEX, "#").replace(NUMBER, "#").take(140)

  fun compute(baseline: List<LogEntry>, current: List<LogEntry>): Report {
    val baseErrors = errorCounts(baseline)
    val curErrors = errorCounts(current)
    val baseCrashes = crashCounts(baseline)
    val curCrashes = crashCounts(current)
    val baseTags = tagCounts(baseline)
    val curTags = tagCounts(current)

    fun added(cur: Map<String, Int>, base: Map<String, Int>) =
      cur.filterKeys { it !in base }.map { Item(it.key, it.value) }.sortedByDescending { it.count }.take(MAX_ITEMS)

    val changed = curTags.filter { (tag, n) ->
      val b = baseTags[tag] ?: return@filter false
      val big = maxOf(n, b)
      big >= 10 && (n >= b * 3 || b >= n * 3)
    }.entries.sortedByDescending { kotlin.math.abs(it.value - (baseTags[it.key] ?: 0)) }.take(10)
      .map { "${it.key}: ${baseTags[it.key]} → ${it.value}" }

    return Report(
      baseline.size, current.size,
      added(curErrors, baseErrors), added(baseErrors, curErrors),
      added(curCrashes, baseCrashes), added(baseCrashes, curCrashes),
      added(curTags, baseTags), changed
    )
  }

  fun toMarkdown(report: Report, baselineName: String, currentName: String): String = buildString {
    append("## Session comparison\n\n")
    append("- **Baseline:** ").append(baselineName).append(" (").append(report.baselineSize).append(" lines)\n")
    append("- **Current:** ").append(currentName).append(" (").append(report.currentSize).append(" lines)\n\n")
    if (!report.hasDifferences) {
      append("No significant differences found.\n")
      return@buildString
    }
    section("New crashes / ANR", report.newCrashes)
    section("New error types", report.newErrors)
    section("Error types that disappeared", report.goneErrors)
    section("Crashes that disappeared", report.goneCrashes)
    section("New tags", report.newTags)
    if (report.changedTags.isNotEmpty()) {
      append("### Tags with changed volume\n\n")
      report.changedTags.forEach { append("- `").append(it).append("`\n") }
      append('\n')
    }
  }

  private fun StringBuilder.section(title: String, items: List<Item>) {
    if (items.isEmpty()) return
    append("### ").append(title).append(" (").append(items.size).append(")\n\n")
    items.forEach { append("- `").append(it.text.replace('`', '\'')).append("` ×").append(it.count).append('\n') }
    append('\n')
  }

  private fun errorCounts(entries: List<LogEntry>): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    for (e in entries) {
      if (e.level != "ERROR" && e.level != "ASSERT") continue
      if (StackFolder.isFrame(e.message)) continue
      out.merge("${e.tag}: ${normalize(e.message)}", 1, Int::plus)
    }
    return out
  }

  private fun crashCounts(entries: List<LogEntry>): Map<String, Int> {
    val tracker = CrashTracker()
    tracker.feed(entries)
    return tracker.groups().associate { "${it.kind.name} ${it.packageName} ${it.title.take(100)}" to it.count }
  }

  private fun tagCounts(entries: List<LogEntry>): Map<String, Int> {
    val out = HashMap<String, Int>()
    for (e in entries) if (e.tag.isNotBlank()) out.merge(e.tag, 1, Int::plus)
    return out
  }
}
