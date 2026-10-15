package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry

enum class CrashKind { JAVA, ANR, NATIVE }

/** Группа одинаковых крашей (по сигнатуре). [lastEntry] — запись, на которую можно перейти в логе. */
data class CrashGroup(
  val kind: CrashKind,
  val signature: String,
  val title: String,
  val packageName: String,
  val firstSeen: Long,
  val sample: List<String>,
  var count: Int,
  var lastSeen: Long,
  var lastEntry: LogEntry
)

/**
 * Находит в потоке Java-краши (`FATAL EXCEPTION`), ANR и нативные падения и группирует одинаковые.
 * Кормится инкрементально: [feed] принимает окно логов целиком (без фильтра) и разбирает только
 * записи, появившиеся после последней обработанной. Группы переживают выпадение строк из окна.
 */
class CrashTracker {
  private class Pending(val pid: Int, val first: LogEntry, val startedAt: Long) {
    val lines = ArrayList<String>()
    var packageName = first.packageName
    var exception: String? = null
    var firstFrame: String? = null
    var appFrame: String? = null
  }

  private val pending = HashMap<Int, Pending>()
  private val groups = LinkedHashMap<String, CrashGroup>()
  private val nativeSeen = HashMap<Int, Long>() // pid -> время последнего нативного падения (libc и DEBUG пишут одно и то же)
  private var lastFed: LogEntry? = null

  /** true — набор групп изменился (есть новый краш или увеличился счётчик). */
  @Synchronized
  fun feed(snapshot: List<LogEntry>): Boolean {
    if (snapshot.isEmpty()) {
      lastFed = null
      return false
    }
    val from = firstUnprocessed(snapshot)
    var changed = false
    for (i in from until snapshot.size) changed = accept(snapshot[i]) or changed
    lastFed = snapshot.last()
    changed = flushStale(snapshot.last().timestamp) or changed
    return changed
  }

  @Synchronized
  // Копии: группы внутри трекера мутируются, а UI сравнивает прежний и новый список (новый краш / рост счётчика).
  fun groups(): List<CrashGroup> = groups.values.map { it.copy() }.sortedByDescending { it.lastSeen }

  @Synchronized
  fun clear() {
    pending.clear()
    groups.clear()
    nativeSeen.clear()
    lastFed = null
  }

  private fun firstUnprocessed(snapshot: List<LogEntry>): Int {
    val last = lastFed ?: return 0
    for (i in snapshot.indices.reversed()) if (snapshot[i] === last) return i + 1
    return 0 // окно очищено/пересоздано — все записи новые
  }

  private fun accept(e: LogEntry): Boolean {
    var changed = false
    val p = pending[e.pid]
    if (p != null) {
      if (e.tag == "AndroidRuntime" && p.lines.size < MAX_LINES) {
        absorb(p, e)
        if (p.exception != null && (p.appFrame != null || p.lines.size >= RESOLVE_LINES)) {
          changed = commit(pending.remove(e.pid)!!)
        }
        return changed
      }
      changed = commit(pending.remove(e.pid)!!)
    }
    when {
      e.tag == "AndroidRuntime" && e.message.startsWith("FATAL EXCEPTION") -> {
        pending[e.pid] = Pending(e.pid, e, e.timestamp).also { it.lines.add(e.message) }
      }
      e.message.startsWith("ANR in ") -> changed = anr(e) or changed
      e.message.startsWith("Fatal signal ") && (e.tag == "libc" || e.tag == "DEBUG") -> changed = native(e) or changed
    }
    return changed
  }

  private fun absorb(p: Pending, e: LogEntry) {
    val m = e.message
    p.lines.add(m)
    if (m.startsWith("Process: ")) {
      p.packageName = m.removePrefix("Process: ").substringBefore(',').trim().substringBefore(':')
    } else if (p.exception == null && EXCEPTION.containsMatchIn(m) && !m.trimStart().startsWith("at ")) {
      p.exception = m.trim()
    } else if (StackFolder.isFrame(m)) {
      val frame = m.trim().removePrefix("at ")
      if (p.firstFrame == null) p.firstFrame = frame
      if (p.appFrame == null && p.packageName.isNotBlank() && frame.startsWith(p.packageName)) p.appFrame = frame
    }
  }

  private fun flushStale(now: Long): Boolean {
    var changed = false
    val it = pending.entries.iterator()
    val stale = ArrayList<Pending>()
    while (it.hasNext()) {
      val p = it.next().value
      if (now - p.startedAt > STALE_MS) { stale.add(p); it.remove() }
    }
    for (p in stale) changed = commit(p) or changed
    return changed
  }

  private fun commit(p: Pending): Boolean {
    val exception = p.exception ?: p.lines.getOrNull(1) ?: "FATAL EXCEPTION"
    val type = exception.substringBefore(':').trim()
    val where = p.appFrame ?: p.firstFrame ?: ""
    val signature = "JAVA|${p.packageName}|$type|${where.substringBefore('(')}"
    return record(CrashKind.JAVA, signature, exception, p.packageName, p.first, p.lines)
  }

  private fun anr(e: LogEntry): Boolean {
    val pkg = e.message.removePrefix("ANR in ").substringBefore(' ').trim()
    val sig = "ANR|$pkg"
    return record(CrashKind.ANR, sig, e.message.lineSequence().first(), pkg, e, listOf(e.message))
  }

  private fun native(e: LogEntry): Boolean {
    val prev = nativeSeen[e.pid]
    nativeSeen[e.pid] = e.timestamp
    if (prev != null && kotlin.math.abs(e.timestamp - prev) < NATIVE_DEDUP_MS) return false
    if (nativeSeen.size > 200) nativeSeen.clear()
    val proc = NATIVE_PROC.find(e.message)?.groupValues?.get(1) ?: e.packageName
    val sig = "NATIVE|$proc|" + e.message.substringBefore(" in tid").substringBefore(", code").trim()
    return record(CrashKind.NATIVE, sig, e.message, proc.ifBlank { e.packageName }, e, listOf(e.message))
  }

  private fun record(kind: CrashKind, signature: String, title: String, pkg: String, at: LogEntry, sample: List<String>): Boolean {
    val existing = groups[signature]
    if (existing != null) {
      existing.count++
      existing.lastSeen = at.timestamp
      existing.lastEntry = at
      return true
    }
    if (groups.size >= MAX_GROUPS) groups.remove(groups.keys.first())
    groups[signature] = CrashGroup(kind, signature, title, pkg, at.timestamp, sample.take(MAX_SAMPLE), 1, at.timestamp, at)
    return true
  }

  private companion object {
    // "java.lang.IllegalStateException: boom", "kotlin.KotlinNullPointerException", "Caused by: …" не считаем заголовком.
    val EXCEPTION = Regex("""^\s*[\w.$]+(Exception|Error|Throwable)\b""")
    const val MAX_LINES = 400
    const val RESOLVE_LINES = 40
    const val MAX_SAMPLE = 80
    const val MAX_GROUPS = 100
    const val STALE_MS = 3000L
    const val NATIVE_DEDUP_MS = 5000L
    val NATIVE_PROC = Regex("""pid \d+ \(([^)]+)\)""")
  }
}
