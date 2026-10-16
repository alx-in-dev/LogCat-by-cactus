package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogLevel
import com.android.tools.idea.logcat.standalone.core.LogQueryMatcher
import com.android.tools.idea.logcat.standalone.core.LogcatAction
import com.android.tools.idea.logcat.standalone.core.LogcatFilterCriteria
import com.android.tools.idea.logcat.standalone.core.LogcatFilterEngine
import com.android.tools.idea.logcat.standalone.core.LogcatSessionController
import com.android.tools.idea.logcat.standalone.core.LogcatUiState
import com.android.tools.idea.logcat.standalone.core.UiHost
import com.cactus.logcactus.presentation.logcat.BugReport
import com.cactus.logcactus.presentation.logcat.RowDetails
import com.cactus.logcactus.presentation.logcat.UrlDetails
import com.cactus.logcactus.presentation.logcat.CanvasFolder
import com.cactus.logcactus.presentation.logcat.CanvasItem
import com.cactus.logcactus.presentation.logcat.CrashGroup
import com.cactus.logcactus.presentation.logcat.CrashTracker
import com.cactus.logcactus.presentation.logcat.LogSnapshotIndex
import com.cactus.logcactus.application.logcat.QueryHistoryService
import com.cactus.logcactus.domain.logcat.QueryHistoryRepository
import com.cactus.logcactus.infrastructure.logcat.FileQueryHistoryRepository
import com.cactus.logcactus.presentation.logcat.LevelColors
import com.cactus.logcactus.presentation.logcat.LogRenderCache
import com.cactus.logcactus.presentation.logcat.ProcessOption
import com.cactus.logcactus.presentation.logcat.RenderedLine
import com.cactus.logcactus.presentation.logcat.RowFold
import com.cactus.logcactus.presentation.logcat.SessionDiff
import com.cactus.logcactus.presentation.logcat.StackFolder
import com.cactus.logcactus.standalone.AppCatalog
import com.cactus.logcactus.standalone.AppInfo
import com.cactus.logcactus.standalone.ActionResult
import com.cactus.logcactus.standalone.DeviceActions
import com.cactus.logcactus.standalone.DeviceCatalog
import com.cactus.logcactus.standalone.DeviceInfoCollector
import com.cactus.logcactus.standalone.FileChoosers
import com.cactus.logcactus.standalone.MdnsService
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.WifiAdb
import kotlinx.coroutines.flow.collectLatest
import com.cactus.logcactus.standalone.ViewSettingsStore
import com.cactus.logcactus.standalone.ViewSettings
import com.cactus.logcactus.standalone.ForegroundAppParser
import com.cactus.logcactus.standalone.ForegroundAppTracker
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import com.cactus.logcactus.standalone.TagPalette
import com.cactus.logcactus.standalone.ThemePalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

class ComposeUiBridge(
  private val deviceProvider: SimpleDeviceProvider,
  private val deviceCatalog: DeviceCatalog,
  // null — настройки вида не читаются и не пишутся (тесты, превью): иначе они зависели бы от файла пользователя.
  private val viewSettingsStore: ViewSettingsStore? = null
) : UiHost {
  private val filterEngine = LogcatFilterEngine()
  private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
  private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var renderJob: Job? = null
  private var deviceRefreshJob: Job? = null
  private var pendingState: LogcatUiState? = null
  private var initialized = false
  private var controller: LogcatSessionController? = null
  private var latestSnapshot: List<LogEntry> = emptyList()
  private var palette: ThemePalette = ThemePalette.load()
  private var paletteStamp: Long = themeFileStamp()
  private var selecting = false
  private val queryHistoryRepository: QueryHistoryRepository = FileQueryHistoryRepository()
  private val queryHistoryService = QueryHistoryService(queryHistoryRepository, MAX_HISTORY)
  private val snapshotIndex = LogSnapshotIndex(ALL_PROCESSES_LABEL, KNOWN_VALUES_LIMIT, filterEngine)
  private val renderCache = LogRenderCache(
    signatureProvider = { renderSignature() },
    lineFormatter = { entry, previousTag -> formatLine(entry, previousTag) },
    lineColorProvider = { level -> levelColor(level) }
  )

  var paused by mutableStateOf(false)
  // Для баннера "нет логов под текущий фильтр" (LogcatMainPanel.isLogsMissing()) —
  // отличаем "логов вообще не было" от "все логи отфильтрованы".
  var hasReceivedAnyLogs by mutableStateOf(false)
  var follow by mutableStateOf(true)
  var wrapLines by mutableStateOf(true)
  // По умолчанию выключено — как в реальном DEFAULT_STANDARD/DEFAULT_COMPACT
  // (TagFormat.hideDuplicates = false в обоих пресетах).
  var hideDuplicateTags by mutableStateOf(false)
  var logFontSize by mutableStateOf(13)
  var level by mutableStateOf("VERBOSE")
  var query by mutableStateOf("")
  // Как в Logcat Android Studio: текст ищется как подстрока, регулярное выражение включается ключом `key~:` (tag~:Net.*, -message~:foo|bar).
  // Общего переключателя Regex больше нет.
  val regex: Boolean = false
  var caseSensitive by mutableStateOf(false)
  var allProcesses by mutableStateOf(true)
  var showTime by mutableStateOf(true)
  var showTag by mutableStateOf(true)
  // Реальный Logcat (FormattingOptions в JetBrains/android) держит appName
  // (пакет) и processName (PID/имя процесса) как ДВА отдельных toggle'а —
  // LogCactus раньше сваливал их в одну колонку "Process" (см. ai/logcat_v2_reference.md).
  var showAppName by mutableStateOf(true)
  var showProcess by mutableStateOf(true)
  var timeWidth by mutableStateOf(12)
  var tagWidth by mutableStateOf(16)
  var appNameWidth by mutableStateOf(24)
  var processWidth by mutableStateOf(24)
  var visibleRows by mutableStateOf<List<RenderedLine>>(emptyList())

  var devices by mutableStateOf<List<DeviceRef>>(emptyList())
  var selectedDevice by mutableStateOf<DeviceRef?>(null)
  var processOptions by mutableStateOf(listOf(ProcessOption.all(ALL_PROCESSES_LABEL)))
  var selectedProcess by mutableStateOf(ProcessOption.all(ALL_PROCESSES_LABEL))
  var queryHistory by mutableStateOf<List<String>>(emptyList())
  var favoriteQueries by mutableStateOf<Set<String>>(emptySet())
  var knownTags by mutableStateOf<List<String>>(emptyList())
  var knownPackages by mutableStateOf<List<String>>(emptyList())
  var knownProcesses by mutableStateOf<List<String>>(emptyList())
  // "app:" — собственная фича LogCactus (пикер приложений устройства с
  // иконками), не часть оригинального Logcat.
  var knownApps by mutableStateOf<List<AppInfo>>(emptyList())
  private val appCatalog = AppCatalog()
  private var appsLoadingForDevice: String? = null
  val levelOptions = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR")

  // package:mine раскрывается в приложение, которое сейчас на экране устройства.
  // Пока фильтр mine не используется, трекер не опрашивает устройство вообще.
  var followedPackage by mutableStateOf("")
  var minePinned by mutableStateOf(false)
  val mineActive: Boolean get() = MINE_IN_QUERY.containsMatchIn(query)
  private val foregroundTracker = ForegroundAppTracker()
  private var foregroundJob: Job? = null

  private fun filterCriteria(q: String) = LogcatFilterCriteria(level, q, regex, caseSensitive, followedPackage)

  // Клик по чипу: закрепить текущее приложение / снова следовать за экраном.
  fun toggleMinePin() {
    if (followedPackage.isBlank()) return
    minePinned = !minePinned
  }

  private fun startForegroundTracking() {
    foregroundJob = uiScope.launch {
      while (true) {
        val serial = selectedDevice?.id
        if (serial != null && mineActive && !minePinned) {
          val pkg = withContext(Dispatchers.IO) { foregroundTracker.current(serial) }
          // Экран/лаунчер/шторка — не "моё" приложение: остаёмся на прежнем.
          if (pkg != null && !ForegroundAppParser.isSystemShell(pkg) && pkg != followedPackage && !minePinned) {
            followedPackage = pkg
            applyLocalFilter()
          }
        }
        delay(FOREGROUND_POLL_INTERVAL_MS)
      }
    }
  }

  fun bind(controller: LogcatSessionController) {
    this.controller = controller
    // Буфер переполняется — первыми вытесняются записи, которые фильтр всё равно не показывает.
    controller.visibleEntryPredicate = {
      val matcher = LogQueryMatcher(query, regex, caseSensitive, followedPackage)
      val lvl = level
      val process = selectedProcess
      val pred: (LogEntry) -> Boolean = { e -> LogLevel.isAtLeast(e.level, lvl) && matcher.matches(e) && (process.all || process.matches(e)) }
      pred
    }
    queryHistory = queryHistoryService.load()
    favoriteQueries = queryHistoryService.loadFavorites()
    refreshDevices()
    startForegroundTracking()
    // В реальной Studio DeviceComboBox подписан на IDeviceChangeListener и
    // видит подключение устройства мгновенно; здесь adb дёргается процессом
    // напрямую (без ddmlib), поэтому эквивалент — периодический опрос `adb
    // devices`, иначе устройство, подключённое ПОСЛЕ запуска приложения,
    // никогда не попадало бы в список (baг, замеченный пользователем).
    deviceRefreshJob = uiScope.launch {
      while (true) {
        delay(DEVICE_POLL_INTERVAL_MS)
        val found = withContext(Dispatchers.IO) { deviceCatalog.listDevices() }
        applyDeviceList(found)
      }
    }
  }

  fun dispose() {
    // Debounce мог не успеть записать последние изменения вида.
    viewSettingsStore?.let { runCatching { it.saveChanged(lastSavedView, currentViewSettings()) } }
    renderJob?.cancel()
    deviceRefreshJob?.cancel()
    foregroundJob?.cancel()
    uiScope.coroutineContext.cancel()
    workerScope.coroutineContext.cancel()
  }

  fun togglePause() {
    paused = !paused
    if (!paused) scheduleRenderLoop()
  }

  fun restartStream() {
    resetBufferBoundState()
    invalidateRenderedCache()
    controller?.restart()
  }

  fun beginSelection() {
    selecting = true
  }

  fun endSelection() {
    if (!selecting) return
    selecting = false
    if (!paused) scheduleRenderLoop()
  }

  // Не на Swing-потоке: зависший adb (после сна/блокировки экрана) иначе замораживал окно при каждом запуске.
  fun refreshDevices() {
    uiScope.launch { applyDeviceList(withContext(Dispatchers.IO) { deviceCatalog.listDevices() }) }
  }

  // Выбранное устройство пропадало из `adb devices` (сон/USB переподключился) — его поток logcat к этому моменту мёртв.
  private var selectedDeviceLost = false

  private fun applyDeviceList(found: List<DeviceRef>) {
    if (found == devices) return // тот же список — не трогаем selectedDevice/поток лишний раз
    devices = found
    val selectedId = selectedDevice?.id
    if (selectedId != null && found.none { it.id == selectedId }) selectedDeviceLost = true
    if (found.isNotEmpty()) {
      // Не дёргаем reconnect, если устройство фактически не поменялось —
      // иначе периодический опрос каждый раз обнулял бы текущий поток
      // логов, даже когда список устройств не менялся вовсе.
      val stillPresent = found.firstOrNull { it.id == selectedDevice?.id }
      val target = stillPresent ?: found.first()
      if (stillPresent != null) {
        selectedDevice = stillPresent
        if (selectedDeviceLost) {
          // Устройство вернулось: без переподписки логи после пробуждения ноутбука так и не возобновлялись.
          selectedDeviceLost = false
          showNotice("Device reconnected — log stream restarted")
          restartStream()
        }
      } else {
        selectedDeviceLost = false
        selectDevice(target)
      }
    }
  }

  // Ленивая загрузка списка приложений устройства для "app:" — не дёргаем adb,
  // пока пользователь реально не начал печатать в этот контекст автодополнения.
  // Список приходит порциями (label раньше, иконка следом), чтобы UI не ждал
  // все ~100-200 приложений сразу.
  fun loadAppsIfNeeded() {
    val device = selectedDevice ?: return
    if (appsLoadingForDevice == device.id) return
    appsLoadingForDevice = device.id
    knownApps = emptyList()
    workerScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val packages = runCatching { appCatalog.listPackages(device.id) }.getOrElse {
        // Сбой adb не должен навсегда блокировать повторную загрузку списка приложений для этого устройства.
        uiScope.launch { if (appsLoadingForDevice == device.id) appsLoadingForDevice = null }
        return@launch
      }
      runCatching {
        appCatalog.resolveAll(device.id, packages) { info ->
          uiScope.launch {
            if (appsLoadingForDevice == device.id) {
              // resolveAll может прислать один пакет дважды (сначала label,
              // потом ту же запись с иконкой) — заменяем, а не дублируем.
              knownApps = knownApps.filterNot { it.packageName == info.packageName } + info
            }
          }
        }
      }
    }
  }

  fun selectDevice(device: DeviceRef) {
    val changed = device.id != selectedDevice?.id
    selectedDevice = device
    if (changed) {
      // Приложение на экране относится к конкретному устройству.
      followedPackage = ""
      minePinned = false
      resetBufferBoundState()
    }
    if (changed) deviceProvider.selectDevice(device)
    // Прогреваем кэш label'ов приложений в фоне сразу при выборе устройства,
    // а не только по первому входу в автодополнение "app:" — иначе колонка
    // "App name" в логе показывала бы голый package до тех пор, пока
    // пользователь явно не откроет пикер app:.
    loadAppsIfNeeded()
  }

  fun selectProcess(process: ProcessOption) {
    selectedProcess = process
    invalidateRenderedCache()
    applyFilters()
  }

  fun toggleAllProcesses(value: Boolean) {
    allProcesses = value
    if (!value && selectedProcess.packageName.isBlank()) {
      selectedProcess = ProcessOption.all(ALL_PROCESSES_LABEL)
    }
    invalidateRenderedCache()
    applyFilters()
  }

  // Live-toggles (в отличие от свободного текстового query, где re-filter по
  // каждому нажатию клавиши был бы расточительным) — иначе переключение
  // чекбокса/дропдауна визуально "не срабатывает" до следующей входящей строки
  // лога или явного Apply.
  fun changeLevel(value: String) {
    level = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun changeShowTime(value: Boolean) {
    showTime = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun changeShowTag(value: Boolean) {
    showTag = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun changeShowProcess(value: Boolean) {
    showProcess = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun changeShowAppName(value: Boolean) {
    showAppName = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun changeCaseSensitive(value: Boolean) {
    caseSensitive = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun changeHideDuplicateTags(value: Boolean) {
    hideDuplicateTags = value
    invalidateRenderedCache()
    applyLocalFilter()
  }

  fun adjustLogFontSize(delta: Int) {
    logFontSize = clamp(logFontSize + delta, 8, 24)
  }

  fun adjustWidth(column: Int, delta: Int) {
    when (column) {
      0 -> timeWidth = clamp(timeWidth + delta, 6, 32)
      1 -> tagWidth = clamp(tagWidth + delta, 6, 48)
      2 -> processWidth = clamp(processWidth + delta, 8, 64)
      3 -> appNameWidth = clamp(appNameWidth + delta, 6, 48)
    }
    invalidateRenderedCache()
    applyLocalFilter()
  }

  // В реальном Logcat фильтр применяется живьём по мере ввода (debounce ~100мс,
  // LogcatHeaderPanel.onFilterUpdate), а в историю запись попадает ОТДЕЛЬНО —
  // только по Enter / потере фокуса / переключению избранного
  // (FilterTextField.kt — addToHistory() вызывается именно оттуда, не из
  // documentChanged). addToHistory=false для live-фильтрации по мере ввода.
  fun applyFilters(addToHistory: Boolean = true) {
    invalidateRenderedCache()
    val normalized = query.trim()
    if (addToHistory && normalized.isNotEmpty()) {
      queryHistory = queryHistoryService.push(queryHistory, normalized)
    }
    controller?.onAction(LogcatAction.ApplyFilter(level, "", normalized, regex, caseSensitive))
    applyLocalFilter()
  }

  // Звезда в реальном Logcat — это НЕ "применить фильтр", а переключение
  // избранного статуса текущего запроса в истории (FavoriteButton.mouseClicked()
  // в FilterTextField.kt): visibleRows это действие не трогает вообще.
  fun toggleFavorite(value: String) {
    favoriteQueries = queryHistoryService.toggleFavorite(favoriteQueries, value)
    if (value.isNotBlank()) {
      queryHistory = queryHistoryService.push(queryHistory, value.trim())
    }
  }

  // ---- Закладки и баг-репорт ------------------------------------------------------------
  // Закладка держит саму запись (по идентичности) и текст строки на момент добавления, поэтому
  // остаётся в баг-репорте, даже когда строка выпала из скользящего окна или скрыта фильтром.
  class Bookmark(val entry: LogEntry, val text: String)

  var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
  var notice by mutableStateOf<String?>(null)
  private var noticeJob: Job? = null
  private val deviceInfoCollector = DeviceInfoCollector()

  fun isBookmarked(entry: LogEntry): Boolean = bookmarks.any { it.entry === entry }

  fun toggleBookmark(line: RenderedLine) {
    if (line.fold?.kind == RowFold.Kind.DETAIL) return // производная строка раскрытия
    toggleBookmarkEntry(line.entry, line.fold?.hidden?.joinToString("\n") { it.text.text } ?: line.text.text)
  }

  // Закладка по записи лога (общая для старого списка и Canvas-рендера): [text] — строка на момент добавления.
  fun toggleBookmarkEntry(entry: LogEntry, text: String) {
    val existing = bookmarks.firstOrNull { it.entry === entry }
    bookmarks = if (existing != null) {
      bookmarks - existing
    } else {
      (bookmarks + Bookmark(entry, text)).sortedBy { it.entry.timestamp }.takeLast(MAX_BOOKMARKS)
    }
  }

  // ---- Поддержка Canvas-рендера (LogCanvasList): переходы, ссылки, отчёт вокруг строки ----
  // Открытие ссылок вынесено в поле, чтобы тесты не запускали браузер.
  var linkOpener: (String) -> Unit = { url -> runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }

  fun searchGoogle(query: String) {
    linkOpener("https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
  }

  /** Запрос прокрутки Canvas-списка к записи; каждый запрос — новый объект, чтобы повтор к той же строке срабатывал. */
  class CanvasScroll(val entry: LogEntry, val item: CanvasItem? = null)

  var canvasScrollRequest by mutableStateOf<CanvasScroll?>(null)

  // Индекс верхней видимой строки Canvas-списка (обновляется при отрисовке) — точка отсчёта для F2/Shift+F2.
  @Volatile var canvasTopIndex = 0
  // Хуки Canvas для баг-репорта: выделенный текст и видимые строки (вызываются из потока UI).
  var canvasSelectionText: () -> String = { "" }
  var canvasVisibleLines: () -> List<String> = { emptyList() }

  fun jumpToBookmarkCanvas(forward: Boolean) {
    val items = canvasItems
    if (bookmarks.isEmpty() || items.isEmpty()) {
      showNotice("No bookmarks: click a level badge to add one")
      return
    }
    val n = items.size
    val from = canvasTopIndex.coerceIn(0, n - 1)
    for (step in 1..n) {
      val i = if (forward) (from + step).mod(n) else (from - step).mod(n)
      val item = items[i]
      if (item.kind != CanvasItem.Kind.DETAIL && bookmarks.any { item.covers(it.entry) }) {
        canvasScrollRequest = CanvasScroll(item.entry)
        return
      }
    }
    showNotice("Bookmarks are outside the current filter")
  }

  fun jumpToCrashCanvas(group: CrashGroup) {
    val entry = canvasRows.firstOrNull { it === group.lastEntry }
    if (entry == null) showNotice("That line is no longer in the buffer or is hidden by the filter") else canvasScrollRequest = CanvasScroll(entry)
  }

  fun copyBugReportAround(entry: LogEntry, copy: (String) -> Unit) {
    val rows = canvasRows
    val i = rows.indexOfFirst { it === entry }
    val lines = if (i < 0) listOf(formatForCanvas(entry).text) else
      rows.subList((i - BUG_REPORT_CONTEXT).coerceAtLeast(0), (i + BUG_REPORT_CONTEXT + 1).coerceAtMost(rows.size)).map { formatForCanvas(it).text }
    launchReport(lines, "around selected line", copy)
  }

  /** Баг-репорт из закладок (в порядке времени записей). */
  fun copyBugReportFromBookmarks(copy: (String) -> Unit) {
    val lines = bookmarks.sortedBy { it.entry.timestamp }.map { it.text }
    if (lines.isEmpty()) { showNotice("No bookmarks: click a level badge to add one"); return }
    launchReport(lines, "bookmarked", copy)
  }

  fun clearBookmarks() {
    bookmarks = emptyList()
  }

  // Индекс ближайшей закладки среди видимых строк (по кругу); -1 — закладок в видимых нет.
  fun findBookmarkRow(fromIndex: Int, forward: Boolean): Int {
    val rows = visibleRows
    if (rows.isEmpty() || bookmarks.isEmpty()) return -1
    val marked = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<LogEntry, Boolean>())
    bookmarks.forEach { marked.add(it.entry) }
    val n = rows.size
    for (step in 1..n) {
      val i = if (forward) (fromIndex + step).mod(n) else (fromIndex - step).mod(n)
      if (rows[i].entry in marked) return i
    }
    return -1
  }

  // Необязательная кнопка в уведомлении (например, "Open folder" у скриншота).
  class NoticeAction(val label: String, val run: () -> Unit)

  var noticeAction by mutableStateOf<NoticeAction?>(null)

  fun showNotice(text: String, durationMs: Long = NOTICE_MS, action: NoticeAction? = null) {
    notice = text
    noticeAction = action
    noticeJob?.cancel()
    noticeJob = uiScope.launch {
      delay(durationMs)
      notice = null
      noticeAction = null
    }
  }

  // Источник строк: вокруг выбранной строки → закладки → хвост видимого списка.
  // (Compose не даёт прочитать drag-выделение из SelectionContainer, поэтому "выделение"
  // задаётся закладками или пунктом контекстного меню.)
  fun copyBugReport(around: RenderedLine? = null, copy: (String) -> Unit) {
    val rows = visibleRows
    val lines: List<String>
    val source: String
    when {
      canvasMode && around == null -> {
        // Выделено — копируем выделенное, иначе строки, видимые на экране.
        val selected = canvasSelectionText().lines().filter { it.isNotBlank() }
        if (selected.isNotEmpty()) { lines = selected; source = "selection" }
        else { lines = canvasVisibleLines(); source = "visible lines" }
      }
      around != null -> {
        val i = rows.indexOfFirst { it.entry === around.entry }.let { if (it < 0) rows.indexOf(around) else it }
        val from = (i - BUG_REPORT_CONTEXT).coerceAtLeast(0)
        val to = (i + BUG_REPORT_CONTEXT + 1).coerceAtMost(rows.size)
        lines = if (i < 0) listOf(around.text.text) else StackFolder.plainLines(rows.subList(from, to))
        source = "around selected line"
      }
      bookmarks.isNotEmpty() -> {
        lines = bookmarks.map { it.text }
        source = "bookmarked"
      }
      else -> {
        lines = if (canvasMode) canvasRows.takeLast(BUG_REPORT_TAIL).map { formatForCanvas(it).text }
        else StackFolder.plainLines(rows.takeLast(BUG_REPORT_TAIL))
        source = "last visible"
      }
    }
    if (lines.isEmpty()) {
      showNotice("Nothing to copy: no visible log lines")
      return
    }
    launchReport(lines, source, copy)
  }

  // Пакет, к которому относятся отчёты и действия: слежение mine → выбранный процесс → (на IO) приложение на экране.
  private fun knownPackage(): String = (if (mineActive) followedPackage else "").ifBlank { selectedProcess.packageName }

  // Подпись цели действий для меню: слежение mine / выбранный процесс, иначе приложение на экране устройства.
  fun actionTargetLabel(): String = knownPackage().ifBlank { "the app on screen" }

  // Блокирующий вызов adb — только из IO-потока.
  private fun resolvePackage(serial: String?, known: String): String = known.ifBlank {
    serial?.let { foregroundTracker.current(it) }?.takeIf { !ForegroundAppParser.isSystemShell(it) }.orEmpty()
  }

  private fun launchReport(lines: List<String>, source: String, copy: (String) -> Unit, knownOverride: String? = null) {
    val serial = selectedDevice?.id
    val minLevel = level
    val filter = query
    val known = knownOverride ?: knownPackage()
    uiScope.launch {
      val text = withContext(Dispatchers.IO) { buildReportText(serial, minLevel, filter, known, lines, source) }
      copy(text)
      showNotice("Bug report copied (${lines.size} lines)")
    }
  }

  // Блокирующий вызов adb — только из IO-потока.
  private fun buildReportText(serial: String?, minLevel: String, filter: String, known: String, lines: List<String>, source: String): String {
    val device = serial?.let { deviceInfoCollector.device(it) }
    val pkg = resolvePackage(serial, known)
    val app = if (serial != null && pkg.isNotBlank()) deviceInfoCollector.app(serial, pkg) else null
    return BugReport.build(java.time.ZonedDateTime.now(), device, app, filter, minLevel, lines, source)
  }

  // ---- Для MCP-сервера (только чтение; вызывается не из Swing-потока) --------------------------
  /** Весь буфер без фильтра окна. Вызывать на Swing-потоке. */
  fun bufferEntries(): List<LogEntry> = latestSnapshot

  /** Отчёт как у кнопки «Copy bug report», но текстом: без буфера обмена и уведомлений. Блокирует поток adb-вызовами. */
  fun bugReportText(lines: List<String>, source: String): String {
    val (serial, lvl, q, known) = runOnSwing { Quad(selectedDevice?.id, level, query, knownPackage()) }
    return buildReportText(serial, lvl, q, known, lines, source)
  }

  /** PNG экрана выбранного устройства; null — устройства нет или снимок не получился. */
  fun screenshotPng(): ByteArray? = runOnSwing { selectedDevice?.id }?.let { deviceActions.screenshot(it) }

  private data class Quad(val a: String?, val b: String, val c: String, val d: String)

  private fun <T> runOnSwing(block: () -> T): T {
    if (javax.swing.SwingUtilities.isEventDispatchThread()) return block()
    var out: Result<T>? = null
    javax.swing.SwingUtilities.invokeAndWait { out = runCatching(block) }
    return out!!.getOrThrow()
  }

  // ---- Краши и ANR ----------------------------------------------------------------------------
  private val crashTracker = CrashTracker()
  var crashGroups by mutableStateOf<List<CrashGroup>>(emptyList())

  fun copyCrashReport(group: CrashGroup, copy: (String) -> Unit) {
    launchReport(group.sample, "${group.kind.name.lowercase()} ×${group.count}", copy, knownOverride = group.packageName)
  }

  // Индекс строки последнего вхождения краша среди видимых; -1 — строка уже вне буфера/фильтра.
  fun crashRowIndex(group: CrashGroup): Int = visibleRows.indexOfFirst { it.entry === group.lastEntry }

  // ---- Действия над устройством/приложением (см. DeviceActions) --------------------------------
  class ConfirmRequest(val title: String, val message: String, val confirmLabel: String, val onConfirm: () -> Unit)

  private val deviceActions = DeviceActions()
  var confirmRequest by mutableStateOf<ConfirmRequest?>(null)
  var deepLinkDialog by mutableStateOf(false)
  var infoDialog by mutableStateOf<String?>(null)

  // Целевое приложение: слежение mine → выбранный процесс → приложение на экране устройства.
  private fun deviceAction(block: (serial: String, pkg: String) -> ActionResult) {
    val serial = selectedDevice?.id ?: run { showNotice("No device selected"); return }
    val known = knownPackage()
    uiScope.launch {
      val result = withContext(Dispatchers.IO) {
        val pkg = resolvePackage(serial, known)
        if (pkg.isBlank()) ActionResult(false, NO_TARGET) else block(serial, pkg)
      }
      showNotice(result.message)
    }
  }

  fun forceStopApp() = deviceAction { s, p -> deviceActions.forceStop(s, p) }

  fun restartApp() = deviceAction { s, p -> deviceActions.restart(s, p) }

  fun clearAppCache() = deviceAction { s, p -> deviceActions.clearCache(s, p) }

  // Очистка данных необратима: сначала определяем пакет и показываем его в подтверждении.
  fun requestClearData() {
    val serial = selectedDevice?.id ?: run { showNotice("No device selected"); return }
    val known = knownPackage()
    uiScope.launch {
      val pkg = withContext(Dispatchers.IO) { resolvePackage(serial, known) }
      if (pkg.isBlank()) {
        showNotice(NO_TARGET)
        return@launch
      }
      confirmRequest = ConfirmRequest("Clear app data", "All data of $pkg on this device will be erased. This cannot be undone.", "Clear data") {
        uiScope.launch {
          val r = withContext(Dispatchers.IO) { deviceActions.clearData(serial, pkg) }
          showNotice(r.message)
        }
      }
    }
  }

  fun openDeepLink(uri: String) {
    val serial = selectedDevice?.id ?: run { showNotice("No device selected"); return }
    uiScope.launch { showNotice(withContext(Dispatchers.IO) { deviceActions.openDeepLink(serial, uri, null) }.message) }
  }

  fun showAppInfo() {
    val serial = selectedDevice?.id ?: run { showNotice("No device selected"); return }
    val known = knownPackage()
    uiScope.launch {
      val text = withContext(Dispatchers.IO) {
        val pkg = resolvePackage(serial, known)
        if (pkg.isBlank()) NO_TARGET else deviceActions.appDetails(serial, pkg)?.toText() ?: "No package info for $pkg"
      }
      infoDialog = text
    }
  }

  // PNG сохраняется в ~/Pictures/LogCactus и кладётся в буфер обмена как картинка.
  fun takeScreenshot() {
    val serial = selectedDevice?.id ?: run { showNotice("No device selected"); return }
    showNotice("Taking screenshot…")
    uiScope.launch {
      val saved = withContext(Dispatchers.IO) {
        val bytes = deviceActions.screenshot(serial) ?: return@withContext null
        val file = deviceActions.saveScreenshot(bytes)
        runCatching {
          val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
          java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
        }
        file
      }
      if (saved == null) {
        showNotice("Screenshot failed")
      } else {
        showNotice(
          "Screenshot saved and copied: ${saved.name}",
          durationMs = SCREENSHOT_NOTICE_MS,
          action = NoticeAction("Open folder") { workerScope.launch(Dispatchers.IO) { DeviceActions.revealInFileManager(saved) } }
        )
      }
    }
  }

  private class ImageSelection(private val image: java.awt.Image) : java.awt.datatransfer.Transferable {
    override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor == java.awt.datatransfer.DataFlavor.imageFlavor
    override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any =
      if (isDataFlavorSupported(flavor)) image else throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
  }

  // ---- Сессии: сохранить / открыть / сравнить ---------------------------------------------------
  // Открытие сессии создаёт новую вкладку; её создаёт окно приложения (мост про вкладки не знает).
  var onOpenSession: ((title: String, session: SessionFile.Session) -> Unit)? = null
  var compareReport by mutableStateOf<String?>(null)

  // Вкладка-сессия: без устройства и без потока, строки подаются целиком один раз.
  fun loadSession(session: SessionFile.Session) {
    render(LogcatUiState(null, null, "VERBOSE", "", "", true, false, session.entries))
    val meta = listOf(session.meta["device"], session.meta["app"]).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    showNotice("Session: ${session.entries.size} lines" + if (meta.isNotEmpty()) " · $meta" else "" +
      (session.meta[SessionFile.TRUNCATED_KEY]?.let { " · first $it lines skipped (limit)" } ?: ""))
  }

  // Что сохранять: весь буфер или только то, что сейчас видно по фильтру/уровню/процессу.
  class SaveRequest(val allCount: Int, val visibleCount: Int)

  var saveRequest by mutableStateOf<SaveRequest?>(null)

  // Записи, которые сейчас проходят фильтр (как в списке, но без свёртки стектрейсов и раскрытия JSON).
  fun visibleEntries(): List<LogEntry> {
    val filtered = filterEngine.filter(latestSnapshot, filterCriteria(query))
    return if (selectedProcess.all) filtered else filtered.filter { selectedProcess.matches(it) }
  }

  // Если фильтр ничего не отсекает, выбор не нужен — сохраняем сразу.
  fun requestSaveSession() {
    val all = latestSnapshot.size
    if (all == 0) {
      showNotice("Nothing to save: the log is empty")
      return
    }
    val visible = visibleEntries().size
    if (visible == all) saveSession(visibleOnly = false) else saveRequest = SaveRequest(all, visible)
  }

  fun saveSession(visibleOnly: Boolean) {
    val entries = if (visibleOnly) visibleEntries() else latestSnapshot
    if (entries.isEmpty()) {
      showNotice("Nothing to save: no lines match the current filter")
      return
    }
    val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val file = FileChoosers.save("Save session", "logcat-$stamp.${SessionFile.EXTENSION}", SessionFile.EXTENSION) ?: return
    val serial = selectedDevice?.id
    val known = knownPackage()
    val filter = query
    uiScope.launch {
      val message = withContext(Dispatchers.IO) {
        val device = serial?.let { deviceInfoCollector.device(it) }
        val pkg = resolvePackage(serial, known)
        val app = if (serial != null && pkg.isNotBlank()) deviceInfoCollector.app(serial, pkg) else null
        val meta = linkedMapOf(
          "saved" to java.time.ZonedDateTime.now().toString(),
          "device" to (device?.title.orEmpty() + if (device != null) " · Android ${device.androidRelease} (API ${device.sdk})" else ""),
          "app" to (app?.let { "${it.packageName} ${it.versionName} (${it.versionCode})" } ?: pkg),
          "filter" to filter,
          "scope" to if (visibleOnly) "filtered" else "all"
        )
        runCatching { SessionFile.write(file, meta, entries) }
          .fold({ "Session saved: ${file.name} (${entries.size} lines)" }, { "Save failed: ${it.message}" })
      }
      showNotice(message)
    }
  }

  private fun readSession(file: java.io.File, then: (SessionFile.Session) -> Unit) {
    uiScope.launch {
      val session = withContext(Dispatchers.IO) { runCatching { SessionFile.read(file) }.getOrNull() }
      if (session == null) showNotice("Not a LogCactus session file: ${file.name}") else then(session)
    }
  }

  fun openSession() {
    val file = FileChoosers.open("Open session") ?: return
    readSession(file) { session -> onOpenSession?.invoke(file.nameWithoutExtension, session) }
  }

  // Baseline из файла против текущего буфера этой вкладки.
  fun compareWithSession() {
    val file = FileChoosers.open("Choose baseline session") ?: return
    val current = latestSnapshot
    readSession(file) { baseline ->
      uiScope.launch {
        val md = withContext(Dispatchers.Default) {
          SessionDiff.toMarkdown(SessionDiff.compute(baseline.entries, current), file.nameWithoutExtension, "current buffer")
        }
        compareReport = md
      }
    }
  }

  // ---- adb по Wi-Fi (см. WifiAdb) -----------------------------------------------------------------
  private val wifiAdb = WifiAdb()
  var wifiDialog by mutableStateOf(false)
  var wifiBusy by mutableStateOf(false)
  // Итог последней операции — показывается внутри диалога (уведомление под окном диалога не видно).
  var wifiStatus by mutableStateOf<String?>(null)
  var wifiServices by mutableStateOf<List<MdnsService>>(emptyList())

  val selectedIsNetworkDevice: Boolean get() = selectedDevice?.id?.contains(':') == true

  // Выполняет операцию adb на IO, затем обновляет список устройств и, если известен адрес, выбирает его.
  private fun wifiRun(block: () -> Pair<ActionResult, String?>) {
    if (wifiBusy) return
    wifiBusy = true
    wifiStatus = null
    uiScope.launch {
      try {
        val (result, target, found) = withContext(Dispatchers.IO) {
          val (r, t) = block()
          Triple(r, t, deviceCatalog.listDevices())
        }
        wifiStatus = result.message
        showNotice(result.message, durationMs = WIFI_NOTICE_MS)
        applyDeviceList(found)
        if (result.ok && target != null) found.firstOrNull { it.id == target }?.let { selectDevice(it) }
      } catch (e: Exception) {
        wifiStatus = "Failed: ${e.message ?: e.javaClass.simpleName}"
      } finally {
        wifiBusy = false // иначе после сбоя кнопки диалога оставались бы заблокированными навсегда
      }
    }
  }

  fun wifiEnableFromUsb() {
    val serial = selectedDevice?.id ?: run { showNotice("No device selected"); return }
    wifiRun { wifiAdb.enableFromUsb(serial) }
  }

  fun wifiConnect(hostPort: String) = wifiRun { wifiAdb.connect(hostPort) to hostPort.trim() }

  fun wifiPair(hostPort: String, code: String) = wifiRun { wifiAdb.pair(hostPort, code) to null }

  fun wifiDisconnect() {
    val serial = selectedDevice?.id ?: return
    wifiRun { wifiAdb.disconnect(serial) to null }
  }

  fun wifiScan() {
    if (wifiBusy) return
    wifiBusy = true
    wifiStatus = null
    uiScope.launch {
      try {
        val found = withContext(Dispatchers.IO) { wifiAdb.scan() }
        wifiServices = found
        if (found.isEmpty()) {
          val msg = "Nothing found: turn on Wireless debugging on the phone (same Wi-Fi network)"
          wifiStatus = msg
          showNotice(msg, durationMs = WIFI_NOTICE_MS)
        }
      } catch (e: Exception) {
        wifiStatus = "Scan failed: ${e.message ?: e.javaClass.simpleName}"
      } finally {
        wifiBusy = false
      }
    }
  }

  // Закладки, краши и раскрытия указывают на записи старого буфера: после очистки, Restart или смены устройства их нет.
  private fun resetBufferBoundState() {
    crashTracker.clear()
    crashGroups = emptyList()
    bookmarks = emptyList()
    expandedEntries.clear()
    foldVersion++
  }

  fun clearLogs() {
    resetBufferBoundState()
    invalidateRenderedCache()
    controller?.onAction(LogcatAction.ClearLogs())
  }

  fun pickHistory(value: String) {
    query = value
    applyFilters()
  }

  fun removeFromHistory(value: String) {
    queryHistory = queryHistoryService.remove(queryHistory, value)
    favoriteQueries = favoriteQueries - value
  }

  fun clearHistory() {
    queryHistory = queryHistoryService.clear()
  }

  override fun render(state: LogcatUiState) {
    uiScope.launch {
      // render() зовётся на каждую входящую строку лога — читать и парсить theme.json
      // с диска каждый раз незачем, достаточно перезагружать при изменении файла.
      val stamp = themeFileStamp()
      if (stamp != paletteStamp) {
        paletteStamp = stamp
        palette = ThemePalette.load()
      }
      if (!initialized) {
        level = state.minLevel
        query = state.query
        caseSensitive = state.isCaseSensitive
        bufferBytes = state.maxBytes
        initialized = true
      }
      pendingState = state
      scheduleRenderLoop()
    }
  }

  override fun showError(text: String) {
    System.err.println(text)
  }

  private fun scheduleRenderLoop() {
    if (renderJob?.isActive == true) return
    renderJob = uiScope.launch {
      while (true) {
        delay(RENDER_INTERVAL_MS)
        val rendered = flushPendingRender()
        if (!rendered) {
          renderJob = null
          break
        }
      }
    }
  }

  private suspend fun flushPendingRender(): Boolean {
    val state = pendingState ?: return false
    if (paused || selecting) return false
    pendingState = null

    val criteria = filterCriteria(query)
    val snapshot = state.visibleLogs
    latestSnapshot = snapshot
    hasReceivedAnyLogs = snapshot.isNotEmpty()
    if (bufferLines != snapshot.size) bufferLines = snapshot.size

    val selectedDeviceState = state.selectedDevice
    if (selectedDeviceState != null) selectedDevice = selectedDeviceState

    val selected = selectedProcess
    val allFlag = allProcesses
    var crashChanged = false
    val result = withContext(workerScope.coroutineContext) {
      crashChanged = crashTracker.feed(snapshot)
      // Индекс обновляется по дельте окна (см. LogSnapshotIndex) вместо полного
      // прохода по 5000 строк на каждом кадре. Списки автодополнения — по алфавиту,
      // а не по порядку появления, чтобы подсказки не "скакали" при новых строках.
      val r = snapshotIndex.update(snapshot, criteria, selected, allFlag)
      RenderResult(r.processes, r.rows, r.tags, r.packages, r.processNames)
    }

    // Пока воркер считал, пользователь мог поменять фильтр/процесс: результат по старым критериям не применяем.
    if (criteria != filterCriteria(query) || selected !== selectedProcess || allFlag != allProcesses) {
      if (pendingState == null) pendingState = state
      return true
    }

    if (crashChanged) {
      val groups = crashTracker.groups()
      val fresh = groups.firstOrNull { System.currentTimeMillis() - it.lastSeen < FRESH_CRASH_MS }
      val grew = groups.sumOf { it.count } > crashGroups.sumOf { it.count }
      crashGroups = groups
      if (fresh != null && grew) showNotice("${fresh.kind.name} in ${fresh.packageName.ifBlank { "process" }}: ${fresh.title.take(80)}")
    }
    processOptions = result.processes
    if (result.processes.none { it.label == selectedProcess.label }) {
      // Процесс перезапустился (новый PID) — тот же пакет и имя остаются выбранными.
      val cur = selectedProcess
      selectedProcess = result.processes.firstOrNull {
        !it.all && cur.packageName.isNotBlank() && it.packageName == cur.packageName && it.processName == cur.processName
      } ?: ProcessOption.all(ALL_PROCESSES_LABEL)
    }
    knownTags = result.tags
    knownPackages = result.packages
    knownProcesses = result.processNames
    visibleRows = renderRows(result.rows)
    return true
  }

  // Совпадений для строки истории фильтров (для "99+" в дропдауне funnel-иконки,
  // как в реальном Studio) — не бесконечно долго на больших буферах, т.к.
  // фильтрация сама по себе уже быстрая (используется тем же движком).
  fun countMatches(historyQuery: String): Int {
    val criteria = filterCriteria(historyQuery)
    val filtered = filterEngine.filter(latestSnapshot, criteria)
    return if (selectedProcess.all) filtered.size else filtered.count { selectedProcess.matches(it) }
  }

  private fun applyLocalFilter() {
    val criteria = filterCriteria(query)
    val filtered = filterEngine.filter(latestSnapshot, criteria)
    val narrowed = if (selectedProcess.all) filtered else filtered.filter { selectedProcess.matches(it) }
    visibleRows = renderRows(narrowed)
  }

  // Стектрейсы сворачиваются, JSON/Intent раскрываются уже над отрисованными строками (см. StackFolder):
  // кэш рендера (LogRenderCache) при этом работает с "чистым" списком, а его сдвиг окна не ломается.
  private val stackFolder = StackFolder()
  private var baseRows: List<RenderedLine> = emptyList()
  private val expandedEntries = java.util.IdentityHashMap<LogEntry, Boolean>()

  // Размер буфера логов (байты) — настройка Configure view; строки выше лимита выбрасываются с головы.
  var bufferBytes by mutableStateOf(LogcatUiState.DEFAULT_BUFFER_BYTES)
  var bufferLines by mutableStateOf(0)

  fun adjustBuffer(step: Int) {
    val options = BUFFER_OPTIONS
    val current = options.indices.minByOrNull { kotlin.math.abs(options[it] - bufferBytes) } ?: 0
    val next = options[(current + step).coerceIn(0, options.lastIndex)]
    if (next == bufferBytes) return
    bufferBytes = next
    controller?.onAction(LogcatAction.SetBufferSize(next))
  }

  var foldStacks by mutableStateOf(true)
  // JSON и Intent в сообщениях раскрываются автоматически; клик/меню «Collapse» сворачивает конкретную строку.
  var autoExpandJson by mutableStateOf(true)
  var autoExpandUrls by mutableStateOf(true)

  // Список рисуется на Canvas (LogCanvasList) прямо по LogEntry; RenderedLine/StackFolder строятся только в старом режиме.
  // Canvas-рендер — режим по умолчанию (с 1.3.0). Старый список на Compose-узлах остаётся аварийным откатом:
  // -Dlogcactus.canvas=false.
  val canvasMode: Boolean = System.getProperty("logcactus.canvas")?.equals("true", ignoreCase = true) ?: true
  var canvasRows by mutableStateOf<List<LogEntry>>(emptyList())

  // Строки Canvas-списка: записи после свёртки стектрейсов и раскрытия JSON/Intent (см. CanvasFolder).
  private val canvasFolder = CanvasFolder()
  var canvasItems by mutableStateOf<List<CanvasItem>>(emptyList())
  private var canvasFoldedFor: List<LogEntry>? = null
  private var canvasFoldedVersion = -1

  private fun refoldCanvas() {
    val rows = canvasRows
    if (canvasFoldedFor === rows && canvasFoldedVersion == foldVersion) return
    canvasItems = canvasFolder.fold(
      rows, foldStacks && !foldBypassForSearch, { expandedEntries.containsKey(it) }, expandedEntries.isNotEmpty(), autoExpandJson, autoExpandUrls
    )
    canvasFoldedFor = rows
    canvasFoldedVersion = foldVersion
  }

  private fun refoldAll() {
    if (canvasMode) refoldCanvas() else visibleRows = foldRows()
  }

  // Раскрыть/свернуть заглушку стека или JSON/Intent в строке Canvas-списка.
  fun toggleCanvasExpanded(item: CanvasItem) {
    val key = item.entry
    if (expandedEntries.remove(key) == null) {
      if (expandedEntries.size >= MAX_EXPANDED) expandedEntries.clear()
      expandedEntries[key] = true
    }
    foldVersion++
    refoldCanvas()
  }

  // Есть ли что показывать в списке — независимо от режима (в Canvas-режиме visibleRows всегда пуст, строки в canvasItems).
  val hasVisibleRows: Boolean get() = if (canvasMode) canvasItems.isNotEmpty() else visibleRows.isNotEmpty()

  // previousTag — тег предыдущей строки списка: для hideDuplicateTags повторяющийся тег гасится пробелами.
  fun formatForCanvas(entry: LogEntry, previousTag: String? = null): AnnotatedString = formatLine(entry, previousTag)

  // Ширина одного знака моноширинного шрифта списка в sp: Canvas выставляет её при измерении, нужна для висячего отступа JSON.
  @Volatile var canvasCharSp: Float = 0f

  // Сколько раз строили видимый текст строки (счётчик для тестов: далёкие от экрана строки не должны форматироваться).
  @Volatile var canvasTextBuilds: Long = 0

  // Отступ под колонки (время/тег/…): видимый текст записи заканчивается её сообщением — разность и есть префикс.
  private fun canvasPrefixLength(entry: LogEntry): Int =
    (formatLine(entry, null).length - entry.message.length).coerceIn(0, MAX_CANVAS_INDENT)

  /** Видимый текст строки Canvas-списка: запись целиком; заглушка стека; запись до начала JSON/Intent; дочерняя строка. */
  fun canvasItemText(item: CanvasItem, previousTag: String? = null): AnnotatedString {
    canvasTextBuilds++
    val entry = item.entry
    return when (item.kind) {
      CanvasItem.Kind.ENTRY -> formatLine(entry, previousTag)
      CanvasItem.Kind.STACK -> {
        val label = if (item.expanded) "▾ hide ${item.hidden.size} stack frames" else "▸ ${item.hidden.size} stack frames"
        AnnotatedString(" ".repeat(canvasPrefixLength(entry)) + label)
      }
      CanvasItem.Kind.HEAD -> {
        val base = formatLine(entry, previousTag)
        val cut = base.length - (entry.message.length - item.payloadStart)
        if (base.text.endsWith(entry.message) && cut in 0..base.length) base.subSequence(0, cut) else base
      }
      CanvasItem.Kind.DETAIL ->
        if (item.json) JsonHighlight.line(" ".repeat(canvasPrefixLength(entry)), item.line, jsonColors(), if (wrapLines) canvasCharSp else 0f)
        else AnnotatedString(" ".repeat(canvasPrefixLength(entry)) + item.line)
    }
  }

  // ---- Поиск (Cmd+F) по строкам Canvas-списка ----
  // Совпадения считаются по тому же видимому тексту, что и рисуется; текст строк кэшируется (строки стабильны), пока
  // не изменится вид (колонки, тема). Подсветку рисует Canvas из canvasSearchHighlight.
  class CanvasHighlight(val byRow: Map<Int, List<SearchMatch>>, val current: SearchMatch?)

  var canvasSearchHighlight by mutableStateOf<CanvasHighlight?>(null)
  private val canvasSearchText = java.util.IdentityHashMap<CanvasItem, String>()
  private var canvasSearchSignature = ""

  fun canvasSearchMatches(pattern: Regex, items: List<CanvasItem>): List<SearchMatch> {
    val signature = renderSignature()
    if (signature != canvasSearchSignature || canvasSearchText.size > items.size * 2 + 2000) {
      canvasSearchText.clear()
      canvasSearchSignature = signature
    }
    val result = ArrayList<SearchMatch>()
    for (i in items.indices) {
      val item = items[i]
      val text = canvasSearchText.getOrPut(item) {
        canvasItemText(item, if (hideDuplicateTags && i > 0) items[i - 1].entry.tag else null).text
      }
      for (m in pattern.findAll(text)) {
        if (m.range.isEmpty() && m.value.isEmpty()) continue // пустое regex-совпадение пропускаем, не зацикливаемся
        result.add(SearchMatch(i, m.range.first, m.range.last + 1))
      }
    }
    return result
  }

  fun scrollCanvasToItem(index: Int) {
    val item = canvasItems.getOrNull(index) ?: return
    canvasScrollRequest = CanvasScroll(item.entry, item)
  }

  // Навигация по кадрам стектрейса (кнопки со стрелками): свёрнутая заглушка тоже считается кадром.
  fun jumpToStackFrameCanvas(forward: Boolean) {
    val items = canvasItems
    if (items.isEmpty()) return
    val n = items.size
    val from = canvasTopIndex.coerceIn(0, n - 1)
    for (step in 1..n) {
      val i = if (forward) (from + step).mod(n) else (from - step).mod(n)
      val item = items[i]
      if (item.kind == CanvasItem.Kind.STACK || (item.kind == CanvasItem.Kind.ENTRY && StackFolder.isFrame(item.entry.message))) {
        canvasScrollRequest = CanvasScroll(item.entry, item)
        return
      }
    }
  }

  fun levelColors(level: String): LevelColors = levelColor(level)

  // Тёмная ли тема — по яркости фона строки палитры; от неё зависят цвета JSON и фон карточки.
  fun isDarkTheme(): Boolean = awtToCompose(palette.uiBgEven).luminance() < 0.5f
  fun jsonColors(): JsonColors = if (isDarkTheme()) JsonColors.DARK else JsonColors.LIGHT

  fun canvasSignature(): String = renderSignature()

  private fun renderRows(rows: List<LogEntry>): List<RenderedLine> {
    if (canvasMode) {
      canvasRows = rows
      refoldCanvas()
      return emptyList()
    }
    baseRows = renderCache.render(rows)
    return foldRows()
  }

  // Свёртка зовётся на каждый кадр: если вход (тот же baseRows, те же настройки и набор раскрытых) не менялся,
  // возвращаем прежний результат, а не строим новый список из ~5000 элементов 15 раз в секунду.
  private var foldedFor: List<RenderedLine>? = null
  private var foldedVersion = -1
  private var foldedResult: List<RenderedLine> = emptyList()
  private var foldVersion = 0

  // На время поиска (Cmd+F) свёртка выключается, чтобы искать и внутри стектрейсов.
  var foldBypassForSearch = false
    set(value) {
      if (field == value) return
      field = value
      foldVersion++
      refoldAll()
    }

  private fun foldRows(): List<RenderedLine> {
    if (foldedFor === baseRows && foldedVersion == foldVersion) return foldedResult
    val result = stackFolder.fold(baseRows, foldStacks && !foldBypassForSearch, { expandedEntries.containsKey(it) }, expandedEntries.isNotEmpty(), autoExpandJson, autoExpandUrls)
    foldedFor = baseRows
    foldedVersion = foldVersion
    foldedResult = result
    return result
  }

  fun changeAutoExpandJson(value: Boolean) {
    autoExpandJson = value
    expandedEntries.clear()
    foldVersion++
    refoldAll()
  }

  fun changeAutoExpandUrls(value: Boolean) {
    autoExpandUrls = value
    expandedEntries.clear()
    foldVersion++
    refoldAll()
  }

  // Раскрыта ли сейчас строка с JSON/Intent/URL (для подписи пункта меню).
  fun isDetailExpanded(entry: LogEntry): Boolean {
    val url = UrlDetails.mayContainUrl(entry.message) && RowDetails.expansion(entry.message)?.url == true
    return (if (url) autoExpandUrls else autoExpandJson) != expandedEntries.containsKey(entry)
  }

  fun changeFoldStacks(value: Boolean) {
    foldStacks = value
    foldVersion++
    refoldAll()
  }

  // Раскрыть/свернуть серию кадров стектрейса или JSON/Intent в строке.
  fun toggleExpanded(line: RenderedLine) {
    val key = line.fold?.key ?: line.entry
    if (expandedEntries.remove(key) == null) {
      if (expandedEntries.size >= MAX_EXPANDED) expandedEntries.clear()
      expandedEntries[key] = true
    }
    foldVersion++
    refoldAll()
  }

  private fun renderSignature(): String {
    return listOf(
      level,
      regex.toString(),
      caseSensitive.toString(),
      selectedProcess.label,
      showTime.toString(),
      showTag.toString(),
      showAppName.toString(),
      showProcess.toString(),
      hideDuplicateTags.toString(),
      timeWidth.toString(),
      tagWidth.toString(),
      appNameWidth.toString(),
      processWidth.toString(),
      // label'ы приложений резолвятся в фоне асинхронно (см. loadAppsIfNeeded) —
      // без этого уже отрисованные строки не подхватят имя, когда оно появится.
      knownApps.size.toString(),
      palette.levelError.rgb.toString(),
      palette.levelWarn.rgb.toString(),
      palette.levelInfo.rgb.toString(),
      palette.levelDebug.rgb.toString(),
      palette.levelVerbose.rgb.toString(),
      palette.uiBgEven.rgb.toString()
    ).joinToString("|")
  }

  // render() зовётся на каждую входящую строку, поэтому файл проверяется не чаще раза в секунду,
  // и через File.lastModified() (0 для отсутствующего файла) — без исключения на каждый вызов.
  private var lastThemeCheckAt = 0L
  private var lastThemeStamp = 0L

  private fun themeFileStamp(): Long {
    val now = System.currentTimeMillis()
    if (now - lastThemeCheckAt < THEME_CHECK_INTERVAL_MS) return lastThemeStamp
    lastThemeCheckAt = now
    val path = ThemePalette.resolveConfigPath()
    lastThemeStamp = path?.toFile()?.lastModified() ?: 0L
    return lastThemeStamp
  }

  private fun invalidateRenderedCache() {
    renderCache.invalidate()
  }

  private fun formatLine(entry: LogEntry, previousTag: String?): AnnotatedString {
    return buildAnnotatedString {
      if (showTime) {
        append(fit(TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestamp)), timeWidth))
        append("  ")
      }
      if (showTag) {
        // hideDuplicates: если тег совпадает с предыдущей строкой — гасим его
        // пробелами вместо повтора (TagFormat.hideDuplicates в реальном Logcat,
        // выключено по умолчанию).
        if (hideDuplicateTags && previousTag != null && entry.tag == previousTag) {
          append(" ".repeat(tagWidth))
        } else {
          // Реальный Logcat красит тег отдельным hash-based цветом из палитры
          // (LogcatColors.getTagColor(), TagFormat(colorize = true) — включено
          // по умолчанию в обоих пресетах Standard/Compact).
          withStyle(SpanStyle(color = TagPalette.colorFor(entry.tag))) {
            append(fit(entry.tag, tagWidth))
          }
        }
        append("  ")
      }
      if (showAppName) {
        // appNameFormat в реальном Logcat — человекочитаемое имя приложения,
        // не голый package id (который и так виден через app:/package: в
        // фильтре). Берём уже резолвленный label из AppCatalog (та же
        // инфраструктура, что и у пикера "app:" — см. AppCatalog.cachedLabel,
        // только локальный lookup, без adb на каждую строку лога), с фолбэком
        // на package, пока label ещё не резолвнулся в фоне.
        val app = appCatalog.cachedLabel(entry.packageName)?.takeIf { it.isNotBlank() }
          ?: entry.packageName.ifBlank { entry.processName }
        append(fit(app, appNameWidth))
        append("  ")
      }
      if (showProcess) {
        val proc = if (entry.processName.isBlank()) entry.pid.toString() else entry.processName
        append(fit(proc, processWidth))
        append("  ")
      }
      appendMessage(entry.message)
    }
  }

  // URL в сообщении подчёркивается и помечается аннотацией: клик по нему открывает ссылку (см. экран).
  private fun AnnotatedString.Builder.appendMessage(message: String) {
    if (!message.contains("://")) {
      append(message)
      return
    }
    var last = 0
    for (m in URL_PATTERN.findAll(message)) {
      val url = m.value.trimEnd('.', ',', ';', ')', ']', '}', ':')
      if (url.length < 9) continue
      val start = m.range.first
      append(message.substring(last, start))
      pushStringAnnotation(URL_TAG, url)
      withStyle(SpanStyle(color = LINK_COLOR, textDecoration = TextDecoration.Underline)) { append(url) }
      pop()
      last = start + url.length
    }
    append(message.substring(last))
  }

  private fun fit(value: String, width: Int): String {
    if (value.length == width) return value
    if (value.length < width) return value + " ".repeat(width - value.length)
    if (width <= 1) return value.substring(0, 1)
    return value.substring(0, width - 1) + "…"
  }

  private fun levelColor(level: String): LevelColors = when (level) {
    "ERROR" -> LevelColors(awtToCompose(palette.levelError), awtToCompose(palette.badgeErrorBg), awtToCompose(palette.badgeErrorFg))
    "ASSERT" -> LevelColors(awtToCompose(palette.levelAssert), awtToCompose(palette.badgeAssertBg), awtToCompose(palette.badgeAssertFg))
    "WARN" -> LevelColors(awtToCompose(palette.levelWarn), awtToCompose(palette.badgeWarnBg), awtToCompose(palette.badgeWarnFg))
    "INFO" -> LevelColors(awtToCompose(palette.levelInfo), awtToCompose(palette.badgeInfoBg), awtToCompose(palette.badgeInfoFg))
    "DEBUG" -> LevelColors(awtToCompose(palette.levelDebug), awtToCompose(palette.badgeDebugBg), awtToCompose(palette.badgeDebugFg))
    else -> LevelColors(awtToCompose(palette.levelVerbose), awtToCompose(palette.badgeVerboseBg), awtToCompose(palette.badgeVerboseFg))
  }

  private fun clamp(value: Int, min: Int, max: Int): Int = maxOf(min, minOf(max, value))

  private fun awtToCompose(c: java.awt.Color): Color = Color(c.red, c.green, c.blue, c.alpha)

  private data class RenderResult(
    val processes: List<ProcessOption>,
    val rows: List<LogEntry>,
    val tags: List<String>,
    val packages: List<String>,
    val processNames: List<String>
  )

  @Volatile private var lastSavedView: ViewSettings? = null

  // Настройки вида (Configure view) переживают перезапуск: читаем при создании, пишем при любом изменении
  // (snapshotFlow + collectLatest с паузой = debounce, чтобы не писать файл на каждый клик по "+").
  // Блок стоит в конце класса: все свойства ниже к этому моменту уже инициализированы.
  init {
    viewSettingsStore?.let { store ->
      store.load()?.let { applyViewSettings(it) }
      lastSavedView = currentViewSettings()
      uiScope.launch {
        androidx.compose.runtime.snapshotFlow { currentViewSettings() }.collectLatest {
          delay(VIEW_SETTINGS_SAVE_DELAY_MS)
          val before = lastSavedView
          withContext(Dispatchers.IO) { store.saveChanged(before, it) }
          lastSavedView = it
        }
      }
    }
  }

  private fun currentViewSettings() = ViewSettings(
    showTime, showTag, showAppName, showProcess, timeWidth, tagWidth, appNameWidth, processWidth,
    logFontSize, wrapLines, foldStacks, autoExpandJson, hideDuplicateTags, autoExpandUrls
  )

  private fun applyViewSettings(v: ViewSettings) {
    showTime = v.showTime; showTag = v.showTag; showAppName = v.showAppName; showProcess = v.showProcess
    timeWidth = v.timeWidth; tagWidth = v.tagWidth; appNameWidth = v.appNameWidth; processWidth = v.processWidth
    logFontSize = v.fontSize; wrapLines = v.wrapLines; foldStacks = v.foldStacks
    autoExpandJson = v.autoExpandJson; hideDuplicateTags = v.hideDuplicateTags; autoExpandUrls = v.autoExpandUrls
  }

  companion object {
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    // -Dlogcactus.renderMs=N позволяет подбирать частоту без пересборки.
    private val RENDER_INTERVAL_MS: Long = java.lang.Long.getLong("logcactus.renderMs", 66L)
    private const val THEME_CHECK_INTERVAL_MS = 1000L
    private const val DEVICE_POLL_INTERVAL_MS = 2000L
    private const val FOREGROUND_POLL_INTERVAL_MS = 1500L
    const val URL_TAG = "URL"
    private val URL_PATTERN = Regex("""https?://[^\s"'<>]+""")
    private val LINK_COLOR = Color(0xFF589DF6)
    private const val NOTICE_MS = 2500L
    private const val VIEW_SETTINGS_SAVE_DELAY_MS = 400L
    val BUFFER_OPTIONS = listOf(512L * 1024, 1024L * 1024, 2L * 1024 * 1024, 4L * 1024 * 1024, 8L * 1024 * 1024)
    private const val WIFI_NOTICE_MS = 6000L
    private const val SCREENSHOT_NOTICE_MS = 8000L
    private const val MAX_EXPANDED = 500
    private const val MAX_CANVAS_INDENT = 160
    private const val NO_TARGET = "No target app: open your app on the device or pick a process in the filter"
    private const val FRESH_CRASH_MS = 15_000L
    private const val MAX_BOOKMARKS = 200
    private const val BUG_REPORT_TAIL = 200
    private const val BUG_REPORT_CONTEXT = 25
    private val MINE_IN_QUERY = Regex("""\b(package|app):mine(?![\w.])""", RegexOption.IGNORE_CASE)
    private const val MAX_HISTORY = 30
    private const val KNOWN_VALUES_LIMIT = 500
    const val ALL_PROCESSES_LABEL = "All processes"
  }
}
