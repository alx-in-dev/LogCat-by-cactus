package com.cactus.logcactus.presentation.logcat

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogcatFilterCriteria
import com.android.tools.idea.logcat.standalone.core.LogcatFilterEngine
import kotlin.random.Random
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class LogSnapshotIndexTest {
  private val engine = LogcatFilterEngine()
  private val allLabel = "All processes"
  private val limit = 500

  private fun entry(r: Random): LogEntry {
    val pid = r.nextInt(1, 8)
    return LogEntry(
      r.nextLong(1000), listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR").random(r),
      "Tag${r.nextInt(6)}", pid, if (r.nextInt(4) == 0) "" else "proc$pid",
      if (r.nextInt(3) == 0) "" else "com.app$pid", "msg ${r.nextInt(50)}"
    )
  }

  // Прежняя полная пересборка — эталон.
  private fun reference(snapshot: List<LogEntry>, c: LogcatFilterCriteria, sel: ProcessOption, incl: Boolean): LogSnapshotIndex.Result {
    val filtered = engine.filter(snapshot, c)
    val rows = if (sel.all) filtered else filtered.filter { sel.matches(it) }
    val seen = LinkedHashMap<String, ProcessOption>()
    seen[allLabel] = ProcessOption.all(allLabel)
    snapshot.forEach { e ->
      val o = ProcessOption.fromEntry(e, allLabel)
      if (incl || o.packageName.isNotBlank()) seen.putIfAbsent(o.label, o)
    }
    val procs = seen.values.sortedWith(compareBy<ProcessOption> { if (it.all) 0 else 1 }.thenBy { it.label.lowercase() })
    fun vals(f: (LogEntry) -> String) = snapshot.map(f).filter { it.isNotBlank() }.distinct().sorted().take(limit)
    return LogSnapshotIndex.Result(
      procs, rows, vals { it.tag }, vals { it.packageName }, vals { it.processName.ifBlank { it.pid.toString() } }
    )
  }

  @Test
  fun matchesFullRebuildAcrossSlidesFilterChangesAndClears() {
    val r = Random(42)
    val index = LogSnapshotIndex(allLabel, limit, engine)
    var window: List<LogEntry> = emptyList()
    var criteria = LogcatFilterCriteria("VERBOSE", "", false, false)
    var selected = ProcessOption.all(allLabel)
    val cap = 60
    repeat(3000) { step ->
      when {
        step % 400 == 399 -> window = emptyList()
        else -> repeat(r.nextInt(0, 4)) {
          val next = ArrayList<LogEntry>(window)
          next.add(entry(r))
          window = if (next.size > cap) ArrayList(next.subList(next.size - cap, next.size)) else next
        }
      }
      if (step % 97 == 0) criteria = LogcatFilterCriteria(listOf("VERBOSE", "INFO", "ERROR").random(r), listOf("", "tag:Tag1", "msg 1").random(r), false, false)
      if (step % 131 == 0) selected = if (r.nextBoolean()) ProcessOption.all(allLabel) else ProcessOption.fromEntry(entry(r), allLabel)
      val incl = step % 2 == 0
      val got = index.update(window, criteria, selected, incl)
      val want = reference(window, criteria, selected, incl)
      assertEquals(want.rows, got.rows, "rows @ $step")
      assertEquals(want.processes, got.processes, "processes @ $step")
      assertEquals(want.tags, got.tags, "tags @ $step")
      assertEquals(want.packages, got.packages, "packages @ $step")
      assertEquals(want.processNames, got.processNames, "processNames @ $step")
    }
  }
}
