package com.cactus.logcactus.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.cancel
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogcatFilterCriteria
import com.android.tools.idea.logcat.standalone.core.LogcatFilterEngine
import com.android.tools.idea.logcat.standalone.core.LogLevel
import com.cactus.logcactus.plugin.adb.AdbClient
import com.cactus.logcactus.plugin.adb.AdbDevice
import com.cactus.logcactus.presentation.logcat.BugReport
import com.cactus.logcactus.presentation.logcat.CrashGroup
import com.cactus.logcactus.presentation.logcat.CrashTracker
import com.cactus.logcactus.standalone.DeviceInfoCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сессия просмотра логов в проекте: устройства, буфер записей, фильтр, закладки и краши.
 * [scope] выдаёт платформа — при закрытии проекта все задачи отменяются, процесс adb завершается вместе с потоком.
 * Состояние для UI — Compose snapshot-state, поэтому им можно пользоваться прямо из `@Composable`.
 */
class LogcatSession(
  project: Project,
  private val scope: CoroutineScope,
  private val options: ViewOptions,
  private val monitor: DeviceMonitor,
  initialQuery: String = "",
  private val preferredSerial: String? = null,
  private val live: Boolean = true
) : Disposable {
  private val log = logger<LogcatSession>()
  private val adb = AdbClient(project)
  /** Вызывается при смене фильтра или устройства — владелец вкладки сохраняет состояние. */
  var onChangedHook: () -> Unit = {}
  private val engine = LogcatFilterEngine()
  private val crashTracker = CrashTracker()

  val devices: List<AdbDevice> get() = monitor.devices
  var selected by mutableStateOf<AdbDevice?>(null); private set
  /** Минимальный уровень задаётся только в фильтре (`level:warn`), отдельного переключателя нет. */
  val level: String = "VERBOSE"
  var query by mutableStateOf(initialQuery); private set
  var paused by mutableStateOf(false); private set
  var status by mutableStateOf<String?>(null); private set
  /** Записи, прошедшие фильтр (для показа). Заменяется целиком не чаще раза в [TICK_MS]. */
  var visible by mutableStateOf<List<LogEntry>>(emptyList()); private set
  var totalCount by mutableStateOf(0); private set
  var known by mutableStateOf(com.cactus.logcactus.plugin.ui.KnownValues()); private set
  private val tagSet = java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
  private val packageSet = java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
  private val processSet = java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
  /** Пакет приложения на экране устройства — во что раскрывается `package:mine`. */
  var minePackage by mutableStateOf(""); private set
  var minePinned by mutableStateOf(false); private set
  /** В фильтре есть `package:mine` / `app:mine` — только тогда опрашиваем устройство. */
  val mineActive: Boolean get() = MINE_IN_QUERY.containsMatchIn(query)
  var bookmarks by mutableStateOf<Set<LogEntry>>(emptySet()); private set
  var crashes by mutableStateOf<List<CrashGroup>>(emptyList()); private set

  // Вид — общий для вкладок (ViewOptions); здесь только чтение для UI.
  val softWrap get() = options.softWrap
  val foldStacks get() = options.foldStacks
  val autoExpandJson get() = options.autoExpandJson
  val autoExpandUrls get() = options.autoExpandUrls
  val hideRepeatedTags get() = options.hideRepeatedTags
  val showTimestamp get() = options.showTimestamp
  val showPid get() = options.showPid
  val showTag get() = options.showTag
  val showPackage get() = options.showPackage
  val showLevel get() = options.showLevel
  val timestampFormat get() = options.timestampFormat
  val tagWidth get() = options.tagWidth
  val packageWidth get() = options.packageWidth
  private val bufferLines get() = options.bufferLines
  val history get() = options.history
  val favorites get() = options.favorites

  private val lock = Any()
  private val buffer = ArrayList<LogEntry>()
  private val pending = ConcurrentLinkedQueue<LogEntry>()
  private var streamJob: Job? = null
  private var started = false
  @Volatile private var refilter = true
  @Volatile private var appliedMatchCase = options.matchCase

  /** Запускает опрос устройств и конвейер обновления (один раз; повторные вызовы безвредны). */
  fun start() {
    if (started) return
    started = true
    scope.launch { pump() }
    if (!live) return
    monitor.ensureStarted()
    scope.launch { snapshotFlow { monitor.devices }.collect { applyDevices(it) } }
    scope.launch { followForegroundApp() }
  }

  /** Раз в пару секунд узнаёт приложение на экране, но только пока в фильтре есть `mine` (лишние вызовы adb не нужны). */
  private suspend fun followForegroundApp() {
    val tracker = com.cactus.logcactus.standalone.ForegroundAppTracker()
    while (true) {
      delay(FOREGROUND_POLL_MS)
      val serial = selected?.serial ?: continue
      if (!mineActive || minePinned) continue
      val pkg = withContext(Dispatchers.IO) { tracker.current(serial) }
      // Экран, лаунчер и шторка — не «моё» приложение: остаёмся на прежнем.
      if (pkg != null && !com.cactus.logcactus.standalone.ForegroundAppParser.isSystemShell(pkg) && pkg != minePackage && !minePinned) {
        minePackage = pkg
        refilter = true
      }
    }
  }

  /** Список устройств изменился: выбираем своё (из сохранённой вкладки) или первое подключённое; пропавшее заменяем. */
  private fun applyDevices(found: List<AdbDevice>) {
    val current = selected
    val preferred = preferredSerial?.let { p -> found.firstOrNull { it.serial == p && it.online } }
    when {
      current == null || found.none { it.serial == current.serial } -> {
        val next = preferred ?: found.firstOrNull { it.online }
        if (next?.serial != current?.serial) select(next)
      }
      // Устройство вкладки появилось позже, чем нас запустили на другом.
      !current.online && preferred != null -> select(preferred)
    }
  }

  fun select(device: AdbDevice?) {
    // Приложение на экране относится к конкретному устройству.
    if (device?.serial != selected?.serial) { minePackage = ""; minePinned = false }
    selected = device
    onChangedHook()
    streamJob?.cancel()
    clearBuffer()
    status = null
    if (device == null || !device.online) return
    streamJob = scope.launch {
      try {
        adb.logcat(device.serial).collect { pending.add(it) }
        status = "Stream ended — the device was disconnected or adb stopped"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.warn("logcat stream failed", e)
        status = "Stream failed: ${e.message ?: e.javaClass.simpleName}"
      }
    }
  }

  /** Раз в [TICK_MS] переносит накопленное в буфер, обновляет краши и результат фильтра. */
  private suspend fun pump() {
    while (true) {
      delay(TICK_MS)
      // Match case переключили в другой вкладке — этот фильтр тоже надо пересчитать.
      if (options.matchCase != appliedMatchCase) { appliedMatchCase = options.matchCase; refilter = true }
      // На паузе поток не теряем, но и не даём очереди расти без предела.
      while (pending.size > MAX_ENTRIES) pending.poll()
      if (paused && !refilter) continue
      withContext(Dispatchers.Default) { drain() }
    }
  }

  private fun drain() {
    val added = ArrayList<LogEntry>()
    if (!paused) while (true) added.add(pending.poll() ?: break)
    val criteria = LogcatFilterCriteria(level, query, false, options.matchCase, minePackage)
    val newVisible: List<LogEntry>
    synchronized(this.lock) {
      if (added.isEmpty() && !refilter) return
      buffer.addAll(added)
      var grew = false
      for (e in added) {
        if (tagSet.size < MAX_KNOWN && tagSet.add(e.tag)) grew = true
        if (e.packageName.isNotBlank() && packageSet.size < MAX_KNOWN && packageSet.add(e.packageName)) grew = true
        if (e.processName.isNotBlank() && processSet.size < MAX_KNOWN && processSet.add(e.processName)) grew = true
      }
      if (grew) known = com.cactus.logcactus.plugin.ui.KnownValues(tagSet.toList(), packageSet.toList(), processSet.toList())
      if (buffer.size > bufferLines) trimBuffer(criteria)
      val all = refilter
      refilter = false
      if (added.isNotEmpty() && crashTracker.feed(buffer)) crashes = crashTracker.groups()
      newVisible = if (all) engine.filter(buffer, criteria) else visible + engine.filter(added, criteria)
      totalCount = buffer.size
    }
    visible = if (newVisible.size > bufferLines) newVisible.takeLast(bufferLines) else newVisible
  }

  /**
   * Буфер переполнен: сначала выбрасываются записи, которые фильтр не показывает (шум других процессов не должен
   * вытеснять редкие записи приложения), затем самые старые. Освобождается запас 10% за проход.
   */
  private fun trimBuffer(criteria: LogcatFilterCriteria) {
    val target = (bufferLines * 0.9).toInt()
    val matcher = com.android.tools.idea.logcat.standalone.core.LogQueryMatcher(criteria.query, criteria.isRegex, criteria.isCaseSensitive, criteria.minePackage)
    val keep = BooleanArray(buffer.size) { true }
    var count = buffer.size
    for (i in buffer.indices) {
      if (count <= target) break
      val e = buffer[i]
      if (!(LogLevel.isAtLeast(e.level, criteria.minLevel) && matcher.matches(e))) { keep[i] = false; count-- }
    }
    var i = 0
    while (count > target && i < buffer.size) { if (keep[i]) { keep[i] = false; count-- }; i++ }
    val next = ArrayList<LogEntry>(count)
    for (k in buffer.indices) if (keep[k]) next.add(buffer[k])
    buffer.clear(); buffer.addAll(next)
  }

  private fun clearBuffer() {
    synchronized(lock) {
      buffer.clear()
      pending.clear()
      crashTracker.clear()
      tagSet.clear(); packageSet.clear(); processSet.clear()
      refilter = true
    }
    visible = emptyList()
    totalCount = 0
    bookmarks = emptySet()
    crashes = emptyList()
    known = com.cactus.logcactus.plugin.ui.KnownValues()
  }

  fun clear() {
    val serial = selected?.serial
    clearBuffer()
    if (serial != null) scope.launch { adb.clear(serial) }
  }

  val matchCase get() = options.matchCase

  /** «Cc» в поле фильтра: общая для вкладок настройка; остальные вкладки перефильтруются сами (см. цикл потока). */
  fun toggleMatchCase() { options.applyMatchCase(!options.matchCase); refilter = true }

  fun updateQuery(value: String) { if (value != query) { query = value; refilter = true; onChangedHook() } }

  /** Клик по плашке: закрепить текущее приложение или снова следовать за экраном. */
  fun toggleMinePin() { if (minePackage.isNotBlank()) minePinned = !minePinned }

  /** Запоминает фильтр в общей истории (по Enter / потере фокуса). */
  fun commitQueryToHistory() = options.commitToHistory(query)
  fun toggleFavorite(q: String) = options.toggleFavorite(q)
  fun removeFromHistory(q: String) = options.removeFromHistory(q)

  fun togglePause() { paused = !paused }

  /** Переподключает поток выбранного устройства с чистым буфером. */
  fun restart() { if (live) select(selected) }

  fun toggleBookmark(entry: LogEntry) { bookmarks = if (entry in bookmarks) bookmarks - entry else bookmarks + entry }

  fun clearBookmarks() { bookmarks = emptySet() }

  /** Ближайшая закладка среди [rows] после (до) позиции [from], по кругу; null — закладок нет среди показанных. */
  fun neighbourBookmark(rows: List<LogEntry>, from: Int, forward: Boolean): LogEntry? {
    if (rows.isEmpty() || bookmarks.isEmpty()) return null
    val n = rows.size
    for (step in 1..n) {
      val i = if (forward) (from + step).mod(n) else (from - step).mod(n)
      if (rows[i] in bookmarks) return rows[i]
    }
    return null
  }

  /** Баг-репорт Markdown; сведения об устройстве и версия приложения собираются через adb на IO. */
  suspend fun bugReport(lines: List<LogEntry>, source: String, texts: List<String>? = null): String = withContext(Dispatchers.IO) {
    val serial = selected?.serial
    val collector = DeviceInfoCollector()
    val device = serial?.let { collector.device(it) }
    val pkg = lines.groupingBy { it.packageName }.eachCount().filterKeys { it.isNotBlank() }.maxByOrNull { it.value }?.key
    val app = if (serial != null && pkg != null) collector.app(serial, pkg) else null
    BugReport.build(ZonedDateTime.now(), device, app, query, level, texts ?: lines.map { format(it) }, source)
  }

  /** Копия всего буфера (до фильтра) — для сохранения сессии и сравнения. */
  fun bufferSnapshot(): List<LogEntry> = synchronized(lock) { ArrayList(buffer) }

  /** Показывает записи из файла сессии вместо живого потока; «Restart» возвращает живой режим. */
  fun showSession(entries: List<LogEntry>, name: String) {
    streamJob?.cancel()
    clearBuffer()
    synchronized(lock) { buffer.addAll(entries); refilter = true }
    totalCount = entries.size
    status = if (live) "Session: $name (${entries.size} lines) — press Restart to return to the live log" else null
  }

  override fun dispose() {
    streamJob?.cancel()
    scope.cancel()
  }

  companion object {
    private const val TICK_MS = 100L
    private const val DEVICE_POLL_MS = 3000L
    private const val FOREGROUND_POLL_MS = 1500L
    private val MINE_IN_QUERY = Regex("""\b(package|app):mine(?![\w.])""", RegexOption.IGNORE_CASE)
    private const val MAX_KNOWN = 1000
    const val MAX_ENTRIES = 50_000
    val LEVELS = LogLevel.entries.filter { it != LogLevel.UNKNOWN }.map { it.name }

    fun format(e: LogEntry): String {
      val t = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").format(java.time.Instant.ofEpochMilli(e.timestamp).atZone(java.time.ZoneId.systemDefault()))
      return "$t ${LogLevel.fromString(e.level).shortName}/${e.tag}(${e.pid}): ${e.message}"
    }
  }
}
