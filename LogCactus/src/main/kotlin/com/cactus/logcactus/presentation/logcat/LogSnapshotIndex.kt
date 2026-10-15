package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogcatFilterCriteria
import com.android.tools.idea.logcat.standalone.core.LogcatFilterEngine

/**
 * Инкрементальный индекс над скользящим окном логов (LogcatUiState.visibleLogs).
 *
 * Раньше на каждом кадре (30 Гц) по всем 5000 строкам заново выполнялись: фильтр, сбор
 * списка процессов и три distinct().sorted() для автодополнения. Здесь состояние
 * обновляется только по дельте окна (сколько строк выпало спереди и сколько пришло
 * в конец), а отсортированные списки пересчитываются, только если набор значений изменился.
 * Результаты по содержимому совпадают с прежней полной пересборкой.
 */
class LogSnapshotIndex(
  private val allLabel: String,
  private val valuesLimit: Int,
  private val filterEngine: LogcatFilterEngine
) {
  class Result(
    val processes: List<ProcessOption>,
    val rows: List<LogEntry>,
    val tags: List<String>,
    val packages: List<String>,
    val processNames: List<String>
  )

  private class Counted<T>(val value: T, var count: Int)

  private var previous: List<LogEntry> = emptyList()

  private val tagCounts = HashMap<String, Int>()
  private val packageCounts = HashMap<String, Int>()
  private val processNameCounts = HashMap<String, Int>()
  private val optionCounts = HashMap<String, Counted<ProcessOption>>()

  private var tagsSorted: List<String> = emptyList()
  private var packagesSorted: List<String> = emptyList()
  private var processNamesSorted: List<String> = emptyList()
  private var optionsSorted: List<ProcessOption> = emptyList()
  private var tagsDirty = false
  private var packagesDirty = false
  private var processNamesDirty = false
  private var optionsDirty = true // true: даже для пустого окна в списке должен быть пункт "All processes"

  private var filtered: List<LogEntry> = emptyList()
  private var filterKey: FilterKey? = null

  private data class FilterKey(val criteria: LogcatFilterCriteria, val process: ProcessOption)

  fun update(
    snapshot: List<LogEntry>,
    criteria: LogcatFilterCriteria,
    selected: ProcessOption,
    includeNoPackage: Boolean
  ): Result {
    val key = FilterKey(criteria, selected)
    val shift = slideOffset(previous, snapshot)

    if (shift < 0) {
      resetCounts()
      snapshot.forEach(::add)
      filtered = filterAll(snapshot, criteria, selected)
    } else {
      val kept = previous.size - shift
      for (i in 0 until shift) remove(previous[i])
      for (i in kept until snapshot.size) add(snapshot[i])
      filtered = if (key != filterKey) {
        filterAll(snapshot, criteria, selected)
      } else {
        advanceFiltered(previous, shift, snapshot.subList(kept, snapshot.size), criteria, selected)
      }
    }

    previous = snapshot
    filterKey = key

    if (tagsDirty) { tagsSorted = sortedKeys(tagCounts); tagsDirty = false }
    if (packagesDirty) { packagesSorted = sortedKeys(packageCounts); packagesDirty = false }
    if (processNamesDirty) { processNamesSorted = sortedKeys(processNameCounts); processNamesDirty = false }
    if (optionsDirty) {
      optionsSorted = buildList {
        add(ProcessOption.all(allLabel))
        addAll(
          optionCounts.values.map { it.value }
            .sortedWith(compareBy<ProcessOption> { if (it.all) 0 else 1 }.thenBy { it.label.lowercase() })
        )
      }
      optionsDirty = false
    }
    val processes = if (includeNoPackage) optionsSorted else optionsSorted.filter { it.all || it.packageName.isNotBlank() }
    return Result(processes, filtered, tagsSorted, packagesSorted, processNamesSorted)
  }

  private fun filterAll(source: List<LogEntry>, criteria: LogcatFilterCriteria, selected: ProcessOption): List<LogEntry> {
    val byCriteria = filterEngine.filter(source, criteria)
    return if (selected.all) byCriteria else byCriteria.filter { selected.matches(it) }
  }

  // Прежний результат минус выпавшие спереди строки плюс отфильтрованный хвост.
  private fun advanceFiltered(
    oldSnapshot: List<LogEntry>,
    shift: Int,
    tail: List<LogEntry>,
    criteria: LogcatFilterCriteria,
    selected: ProcessOption
  ): List<LogEntry> {
    var drop = 0
    for (i in 0 until shift) {
      if (drop < filtered.size && filtered[drop] === oldSnapshot[i]) drop++
    }
    val newTail = if (tail.isEmpty()) tail else filterAll(tail, criteria, selected)
    if (drop == 0 && newTail.isEmpty()) return filtered
    val out = ArrayList<LogEntry>(filtered.size - drop + newTail.size)
    for (i in drop until filtered.size) out.add(filtered[i])
    out.addAll(newTail)
    return out
  }

  private fun resetCounts() {
    tagCounts.clear(); packageCounts.clear(); processNameCounts.clear(); optionCounts.clear()
    tagsDirty = true; packagesDirty = true; processNamesDirty = true; optionsDirty = true
  }

  private fun add(e: LogEntry) {
    if (e.tag.isNotBlank() && inc(tagCounts, e.tag)) tagsDirty = true
    if (e.packageName.isNotBlank() && inc(packageCounts, e.packageName)) packagesDirty = true
    val proc = e.processName.ifBlank { e.pid.toString() }
    if (proc.isNotBlank() && inc(processNameCounts, proc)) processNamesDirty = true
    val option = ProcessOption.fromEntry(e, allLabel)
    val counted = optionCounts[option.label]
    if (counted == null) {
      optionCounts[option.label] = Counted(option, 1)
      optionsDirty = true
    } else {
      counted.count++
    }
  }

  private fun remove(e: LogEntry) {
    if (e.tag.isNotBlank() && dec(tagCounts, e.tag)) tagsDirty = true
    if (e.packageName.isNotBlank() && dec(packageCounts, e.packageName)) packagesDirty = true
    val proc = e.processName.ifBlank { e.pid.toString() }
    if (proc.isNotBlank() && dec(processNameCounts, proc)) processNamesDirty = true
    val label = ProcessOption.fromEntry(e, allLabel).label
    val counted = optionCounts[label] ?: return
    if (--counted.count <= 0) {
      optionCounts.remove(label)
      optionsDirty = true
    }
  }

  // true — значение появилось впервые.
  private fun inc(map: HashMap<String, Int>, key: String): Boolean {
    val n = map[key]
    map[key] = (n ?: 0) + 1
    return n == null
  }

  // true — последнее вхождение значения пропало.
  private fun dec(map: HashMap<String, Int>, key: String): Boolean {
    val n = map[key] ?: return false
    if (n <= 1) { map.remove(key); return true }
    map[key] = n - 1
    return false
  }

  private fun sortedKeys(map: HashMap<String, Int>): List<String> = map.keys.sorted().take(valuesLimit)

  // Сколько строк выпало из начала: current = хвост previous после k строк + новые строки.
  // 0 — окно только выросло, -1 — окна не связаны (полная пересборка).
  private fun slideOffset(prev: List<LogEntry>, cur: List<LogEntry>): Int {
    if (prev.isEmpty()) return 0
    if (cur.isEmpty()) return -1
    if (cur.size >= prev.size && samePrefix(prev, cur, prev.size)) return 0
    val first = cur[0]
    for (k in 1 until prev.size) {
      if (prev[k] !== first) continue
      val overlap = prev.size - k
      if (cur.size < overlap) continue
      var same = true
      for (i in 0 until overlap) {
        if (prev[k + i] !== cur[i]) { same = false; break }
      }
      if (same) return k
    }
    return -1
  }

  private fun samePrefix(a: List<LogEntry>, b: List<LogEntry>, count: Int): Boolean {
    for (i in 0 until count) if (a[i] !== b[i]) return false
    return true
  }
}
