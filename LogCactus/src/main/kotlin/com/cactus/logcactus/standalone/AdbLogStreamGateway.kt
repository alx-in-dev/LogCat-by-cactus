package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.AdbLongLogcatParser
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogStreamGateway
import com.android.tools.idea.logcat.standalone.core.Subscription
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdbLogStreamGateway : LogStreamGateway {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val sessions = ConcurrentHashMap<String, Session>()

  override fun subscribe(deviceId: String?, listener: java.util.function.Consumer<LogEntry>): Subscription {
    val key = deviceId.orEmpty()
    val adb = AdbLocator.path()
    val command = buildList {
      add(adb)
      if (!deviceId.isNullOrBlank()) {
        add("-s")
        add(deviceId)
      }
      add("logcat")
      add("-v")
      add("long")
    }

    val process = try {
      ProcessBuilder(command).redirectErrorStream(true).start()
    } catch (e: Exception) {
      throw IllegalStateException("Unable to start adb logcat process", e)
    }

    sessions.remove(key)?.close()
    val resolver = ProcessMetadataResolver(adb, deviceId)
    val session = Session(process)
    live.add(process)
    session.job = scope.launch {
      readLoop(session, listener, resolver)
    }
    sessions[key] = session

    return Subscription {
      sessions.remove(key)?.close()
    }
  }

  override fun clear(deviceId: String?) {
    // Таймаут обязателен: после сна/блокировки экрана `logcat -c` может висеть вечно.
    val args = buildList {
      if (!deviceId.isNullOrBlank()) {
        add("-s")
        add(deviceId)
      }
      add("logcat")
      add("-c")
    }
    AdbShell.exec(args)
  }

  private suspend fun readLoop(
    session: Session,
    listener: java.util.function.Consumer<LogEntry>,
    resolver: ProcessMetadataResolver
  ) = withContext(Dispatchers.IO) {
    val parser = AdbLongLogcatParser()
    var failure: Throwable? = null
    try {
      BufferedReader(InputStreamReader(session.process.inputStream, StandardCharsets.UTF_8)).use { reader ->
        var line = reader.readLine()
        while (line != null) {
          // Сбой обработки одной строки не должен обрывать весь поток.
          try {
            for (entry in parser.acceptLine(line)) listener.accept(resolver.enrich(entry))
          } catch (e: Exception) {
            System.err.println("LogCactus: failed to process a log line: $e")
          }
          line = reader.readLine()
        }
      }
    } catch (e: Exception) {
      failure = e
    }
    // Поток закончился не по нашей воле (устройство отключили, adb упал) — скажем об этом в самом логе.
    if (!session.closed) {
      val why = failure?.let { ": ${it.message}" } ?: ""
      val note = LogEntry(System.currentTimeMillis(), "ERROR", "LogCactus", 0, "logcactus", "", "adb logcat stream ended$why (device disconnected?)")
      runCatching { listener.accept(note) }
    }
  }

  private companion object {
    // Один shutdown-hook на все шлюзы (по одному на вкладку): без него `adb logcat` переживал закрытие приложения (сирота с PPID 1).
    val live: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    init {
      Runtime.getRuntime().addShutdownHook(Thread { live.forEach { runCatching { it.destroyForcibly() } } })
    }
  }

  private class Session(val process: Process) {
    @Volatile var job: Job? = null
    @Volatile var closed = false
    fun close() {
      closed = true
      job?.cancel()
      process.destroy()
      live.remove(process)
    }
  }

  /** [readPs] — таблица pid → имя процесса с устройства (null — не удалось); в тестах подменяется. */
  internal class ProcessMetadataResolver(private val readPs: () -> Map<Int, String>?) {
    constructor(adb: String, deviceId: String?) : this(adbPsReader(adb, deviceId))

    @Volatile private var pidToName: Map<Int, String> = emptyMap()
    @Volatile private var lastRefreshTs = 0L

    fun enrich(entry: LogEntry): LogEntry {
      val processName = resolveProcessName(entry.pid)
      val packageName = derivePackageName(processName, entry.pid)
      return LogEntry(
        entry.timestamp,
        entry.level,
        entry.tag,
        entry.pid,
        processName,
        packageName,
        entry.message
      )
    }

    // pid, которых нет в `ps` после обновления (завершившиеся процессы), с временем пометки: повторно из-за них adb не дёргаем.
    private val missing = HashMap<Int, Long>()
    private val recentRefreshes = ArrayDeque<Long>()

    private fun resolveProcessName(pid: Int): String {
      if (pid <= 0) return ""
      pidToName[pid]?.let { return it }
      val now = System.currentTimeMillis()
      missing[pid]?.let { if (now - it < MISSING_TTL_MS) return "" }
      // Новый pid (например, перезапущенное приложение). Без имени процесса у записи не будет пакета, и фильтр
      // `package:…`/`package:mine` отбросит её навсегда — самые первые строки приложения пропадали из окна.
      // Поэтому имя узнаём синхронно. Бюджет — чтобы начальный дамп буфера с сотнями давно умерших pid не превратился
      // в сотни вызовов `ps`: после MAX_REFRESHES за окно остальные неизвестные pid считаются завершившимися.
      while (recentRefreshes.isNotEmpty() && now - recentRefreshes.first() > BUDGET_WINDOW_MS) recentRefreshes.removeFirst()
      if (recentRefreshes.size >= MAX_REFRESHES) {
        missing[pid] = now
        return ""
      }
      recentRefreshes.addLast(now)
      refresh()
      val name = pidToName[pid]
      if (name == null) {
        if (missing.size > 5000) missing.clear()
        missing[pid] = now
      }
      return name.orEmpty()
    }

    private fun derivePackageName(processName: String, pid: Int): String {
      if (processName.isNotBlank()) {
        val suffix = processName.indexOf(':')
        val base = if (suffix > 0) processName.substring(0, suffix) else processName
        if (base.contains(".")) {
          return base
        }
      }
      val currentPid = Integer.getInteger("logcactus.pid", -1)
      val currentPackage = System.getProperty("logcactus.package", "")
      if (pid == currentPid && currentPackage.isNotBlank()) return currentPackage
      return ""
    }

    private fun refresh() {
      // Метка ставится и при неудаче — иначе недоступный adb перезапускался бы на каждую строку.
      lastRefreshTs = System.currentTimeMillis()
      val next = readPs() ?: return
      pidToName = next
      lastRefreshTs = System.currentTimeMillis()
    }

    companion object {
      private const val PS_TIMEOUT_S = 10L

      fun adbPsReader(adb: String, deviceId: String?): () -> Map<Int, String>? = {
        val command = buildList {
          add(adb)
          if (!deviceId.isNullOrBlank()) { add("-s"); add(deviceId) }
          addAll(listOf("shell", "ps", "-A", "-o", "PID,NAME"))
        }
        val next = HashMap<Int, String>()
        val ok = runCatching {
          val ps = ProcessBuilder(command).redirectErrorStream(true).start()
          // Зависший adb не должен вечно держать чтение: по таймауту процесс убивается и readLine получает EOF.
          java.util.concurrent.CompletableFuture.runAsync(
            { if (ps.isAlive) ps.destroyForcibly() },
            java.util.concurrent.CompletableFuture.delayedExecutor(PS_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
          )
          BufferedReader(InputStreamReader(ps.inputStream, StandardCharsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) { parsePsLine(line, next); line = reader.readLine() }
          }
          ps.waitFor()
        }.isSuccess
        if (ok) next else null
      }

      private const val BUDGET_WINDOW_MS = 10_000L
      private const val MAX_REFRESHES = 8
      private const val MISSING_TTL_MS = 5_000L

      private fun parsePsLine(line: String, out: MutableMap<Int, String>) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("PID")) return
        val parts = trimmed.split("\\s+".toRegex(), limit = 2)
        if (parts.size < 2) return
        val pid = parts[0].toIntOrNull() ?: return
        val name = parts[1].trim()
        if (name.isNotEmpty()) out[pid] = name
      }
    }
  }
}
