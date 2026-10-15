package com.cactus.logcactus.plugin.adb

import com.android.tools.idea.logcat.standalone.core.LogEntry
import java.io.File
import java.util.concurrent.TimeUnit

/** Подставляет имя процесса и пакет в записи по `adb shell ps`; опрос редкий (не чаще 1,5 с) и с таймаутом. */
internal class ProcessNames(private val adb: File, private val serial: String) {
  private var names: Map<Int, String> = emptyMap()

  private val missing = HashMap<Int, Long>()
  private val recentRefreshes = ArrayDeque<Long>()

  fun enrich(e: LogEntry): LogEntry {
    val name = nameOf(e.pid)
    val pkg = name.substringBefore(':').takeIf { it.contains('.') }.orEmpty()
    return LogEntry(e.timestamp, e.level, e.tag, e.pid, name, pkg, e.message)
  }

  /**
   * Новый pid (перезапущенное приложение) узнаётся сразу, а не при следующем плановом опросе: без имени у записи нет пакета,
   * и фильтр `package:…` отбросил бы её навсегда. Бюджет обновлений не даёт начальному дампу с сотнями давно умерших pid
   * превратиться в сотни вызовов `ps`.
   */
  private fun nameOf(pid: Int): String {
    if (pid <= 0) return ""
    names[pid]?.let { return it }
    val now = System.currentTimeMillis()
    missing[pid]?.let { if (now - it < MISSING_TTL_MS) return "" }
    while (recentRefreshes.isNotEmpty() && now - recentRefreshes.first() > BUDGET_WINDOW_MS) recentRefreshes.removeFirst()
    if (recentRefreshes.size >= MAX_REFRESHES) { missing[pid] = now; return "" }
    recentRefreshes.addLast(now)
    refresh()
    val name = names[pid]
    if (name == null) { if (missing.size > 5000) missing.clear(); missing[pid] = now }
    return name.orEmpty()
  }

  private fun refresh() {
    val next = HashMap<Int, String>()
    runCatching {
      val p = ProcessBuilder(adb.path, "-s", serial, "shell", "ps", "-A", "-o", "PID,NAME").redirectErrorStream(true).start()
      if (!p.waitFor(4, TimeUnit.SECONDS)) { p.destroyForcibly(); return }
      p.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { l ->
          val parts = l.trim().split(Regex("\\s+"), limit = 2)
          val pid = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
          parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { next[pid] = it }
        }
      }
    }
    if (next.isNotEmpty()) names = next
  }

  private companion object {
    const val BUDGET_WINDOW_MS = 10_000L
    const val MAX_REFRESHES = 8
    const val MISSING_TTL_MS = 5_000L
  }
}
