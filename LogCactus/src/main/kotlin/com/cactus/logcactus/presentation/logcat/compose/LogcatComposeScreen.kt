package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.cactus.logcactus.presentation.logcat.CrashGroup
import com.cactus.logcactus.presentation.logcat.CrashKind
import com.cactus.logcactus.presentation.logcat.ProcessOption
import com.cactus.logcactus.presentation.logcat.RenderedLine
import com.cactus.logcactus.presentation.logcat.RowDetails
import com.cactus.logcactus.presentation.logcat.RowFold
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UiBg = Color(0xFF1E1F22)
private val PanelBg = Color(0xFF171A1F)
private val ToolbarBg = Color(0xFF23262D)
private val FieldBg = Color(0xFF2A2E35)
private val FieldStroke = Color(0xFF3B424E)
private val Accent = Color(0xFF4A78B9)
private val WarningBannerBg = Color(0xFF5C4A1E)
private val WarningBannerFg = Color(0xFFFFD699)
private val SearchBarBg = Color(0xFF2A2E35)
internal val SearchMatchBg = Color(0xFF5C5233)
internal val SearchCurrentMatchBg = Color(0xFF8A6A1F)
private val TextMain = Color(0xFFE6ECF7)
private val TextMuted = Color(0xFFA4AFBF)
private val ButtonTextSize = 13.sp
private val InputTextSize = 13.sp
private val QueryTextSize = 12.sp

// Дословно из LogcatOccurrenceNavigator.kt (EXCEPTION_LINE_PATTERN) — строка стектрейса
// вида "  at com.foo.Bar.method(File.java:123)".
private const val COMPARE_CHUNK_LINES = 40
private val STACK_FRAME_PATTERN = Regex("^\\s*at .+\\(.+\\)$")
private fun isStackFrameRow(row: RenderedLine): Boolean =
  row.fold?.kind == RowFold.Kind.STACK || STACK_FRAME_PATTERN.matches(row.text.text)

// Cmd+F/Ctrl+F — в реальной Studio лог отрисовывается в стандартном IntelliJ
// Editor, и поиск по нему это НЕ что-то logcat-специфичное, а обычный
// стандартный editor find bar (Cc/W/.* toggles, счётчик результатов,
// prev/next, Enter/Shift+Enter/Escape) — воспроизведено по стандартному
// поведению IDE find-in-editor, т.к. отдельного logcat-кода для этого нет.
data class SearchMatch(val rowIndex: Int, val start: Int, val end: Int)

private fun buildSearchRegex(query: String, caseSensitive: Boolean, wholeWord: Boolean, useRegex: Boolean): Regex? {
  if (query.isEmpty()) return null
  val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
  val pattern = if (useRegex) query else Regex.escape(query).let { if (wholeWord) "\\b$it\\b" else it }
  return runCatching { Regex(pattern, options) }.getOrNull()
}

private fun computeSearchMatches(
  rows: List<RenderedLine>,
  query: String,
  caseSensitive: Boolean,
  wholeWord: Boolean,
  useRegex: Boolean
): List<SearchMatch> {
  val pattern = buildSearchRegex(query, caseSensitive, wholeWord, useRegex) ?: return emptyList()
  val result = mutableListOf<SearchMatch>()
  rows.forEachIndexed { rowIndex, row ->
    for (m in pattern.findAll(row.text.text)) {
      if (m.range.isEmpty() && m.value.isEmpty()) continue // пустое regex-совпадение — пропускаем, не зацикливаемся
      result.add(SearchMatch(rowIndex, m.range.first, m.range.last + 1))
    }
  }
  return result
}

private fun highlightSearchMatches(original: AnnotatedString, rowIndex: Int, matches: List<SearchMatch>, current: SearchMatch?): AnnotatedString {
  val rowMatches = matches.filter { it.rowIndex == rowIndex }
  if (rowMatches.isEmpty()) return original
  return buildAnnotatedString {
    append(original)
    for (m in rowMatches) {
      addStyle(SpanStyle(background = if (m == current) SearchCurrentMatchBg else SearchMatchBg), m.start, m.end)
    }
  }
}

// Правый клик по строке лога — подмножество реального контекстного меню
// Logcat (Copy/Copy Message Text/Search with Google/Clear Logcat; Split
// Right/Split Down/Close — фичи IDE-редактора, тут им соответствуют вкладки
// Logcat/Logcat(2)/+; "Explain This Selection" — AI-фича без бэкенда тут —
// сознательно не реализованы). Через generic androidx.compose.foundation.ContextMenuArea
// (НЕ через text-специфичный ContextMenuArea/SelectionManager, который и
// крашился) — независимый, безопасный путь. Действует на СТРОКУ под курсором
// (её полный видимый текст + чистое сообщение), а не на текущее drag-выделение
// — Compose не даёт публичного доступа к содержимому текущего выделения
// извне SelectionContainer, так что читать именно его нельзя.
private fun rowContextMenuItems(
  line: RenderedLine,
  clipboard: androidx.compose.ui.platform.ClipboardManager,
  bookmarked: Boolean,
  expandLabel: String?,
  onToggleExpand: () -> Unit,
  onToggleBookmark: () -> Unit,
  onBugReport: () -> Unit,
  onSearchGoogle: (String) -> Unit,
  onClearLogcat: () -> Unit
): List<ContextMenuItem> = listOf(
  ContextMenuItem("Copy") { clipboard.setText(AnnotatedString(line.text.text)) },
  ContextMenuItem("Copy Message Text") { clipboard.setText(AnnotatedString(line.message)) },
  ContextMenuItem("Copy Bug Report Around This Line") { onBugReport() },
  ContextMenuItem(if (bookmarked) "Remove Bookmark" else "Add Bookmark") { onToggleBookmark() },
) + (if (expandLabel != null) listOf(ContextMenuItem(expandLabel) { onToggleExpand() }) else emptyList()) + listOf(
  ContextMenuItem("Search with Google") { onSearchGoogle(line.message) },
  ContextMenuItem("Clear Logcat") { onClearLogcat() }
)

// Popup контекстного меню компонуется внутри SelectionContainer списка и наследует его
// LocalSelectionRegistrar: тексты пунктов меню регистрируются как выделяемые, и при активном
// выделении Compose падает ("layouts are not part of the same hierarchy" в
// SelectionManager.convertToContainerCoordinates). DisableSelection отключает регистрацию.
private class NoSelectionContextMenuRepresentation(
  private val delegate: androidx.compose.foundation.ContextMenuRepresentation
) : androidx.compose.foundation.ContextMenuRepresentation {
  @Composable
  override fun Representation(state: androidx.compose.foundation.ContextMenuState, items: () -> List<ContextMenuItem>) {
    androidx.compose.foundation.text.selection.DisableSelection { delegate.Representation(state, items) }
  }
}

// Пункт контекстного меню раскрытия/сворачивания: серия кадров стектрейса или JSON/Intent в строке.
private fun expandMenuLabel(line: RenderedLine, detailExpanded: Boolean): String? {
  val fold = line.fold
  return when {
    fold?.kind == RowFold.Kind.STACK -> if (fold.expanded) "Collapse Stack Frames" else "Expand Stack Frames"
    fold?.kind == RowFold.Kind.DETAIL || fold?.kind == RowFold.Kind.HEAD -> "Collapse JSON / Intent / URL"
    RowDetails.mayBeExpandable(line.message) && RowDetails.expand(line.message) != null ->
      if (detailExpanded) "Collapse JSON / Intent / URL" else "Expand JSON / Intent / URL"
    else -> null
  }
}

private fun openUrl(url: String) {
  runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

// Обычный rememberScrollbarAdapter(horizontalScrollState) тут не годится: один
// ScrollState шарится между ВСЕМИ строками (см. комментарий у horizontalScrollState),
// и maxValue/размер thumb'а отражает только ту строку, что измерилась ПОСЛЕДНЕЙ
// в текущем кадре — при вертикальном скролле LazyColumn перерисовывает other
// строки, среди которых попадаются короткие (не требующие скролла), из-за чего
// индикатор схлопывался/пропадал (замечено пользователем). Вместо этого —
// собственный адаптер поверх независимо отслеживаемого maxContentWidthPx
// (монотонно растущий максимум среди КОГДА-ЛИБО отрисованных строк).
private class StableHorizontalScrollbarAdapter(
  private val offset: () -> Int,
  private val setOffset: (Int) -> Unit,
  private val maxContentWidth: () -> Int,
  private val viewportWidth: () -> Int
) : ScrollbarAdapter {
  override val scrollOffset: Float get() = offset().toFloat()
  override suspend fun scrollTo(containerSize: Int, scrollOffset: Float) {
    setOffset(scrollOffset.toInt().coerceIn(0, maxScrollOffset(containerSize).toInt()))
  }
  // Диапазон берётся по ширине области сообщений (viewportWidth), а не по containerSize полосы прокрутки.
  override fun maxScrollOffset(containerSize: Int): Float =
    (maxContentWidth() - viewportWidth()).coerceAtLeast(0).toFloat()
}

// Сдвигает содержимое по горизонтали на общее для ВСЕХ строк значение offset() (единый горизонтальный
// скролл списка): строки не скроллятся каждая сама по себе, поэтому короткие строки не "сбивают" диапазон.
// Значение читается в фазе размещения — смена сдвига не вызывает рекомпозицию.
private fun Modifier.horizontallyShifted(offset: () -> Int): Modifier = this
  .clipToBounds()
  .layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
    val width = constraints.maxWidth.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: placeable.width
    layout(width, placeable.height) { placeable.placeRelative(-offset(), 0) }
  }

private fun formatBufferSize(bytes: Long): String =
  if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} MB" else "${bytes / 1024} KB"

private fun openGoogleSearch(query: String) = openUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"))

// См. комментарий у CompositionLocalProvider(LocalTextContextMenu provides ...)
// в LogcatComposeScreen — отключает баг-падающее right-click меню Compose
// Desktop 1.6.11, оставляя обычное выделение/Cmd+C рабочими как прежде.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val NoOpTextContextMenu = object : TextContextMenu {
  @Composable
  override fun Area(
    textManager: TextContextMenu.TextManager,
    state: androidx.compose.foundation.ContextMenuState,
    content: @Composable () -> Unit
  ) {
    content()
  }
}

private data class FilterSuggestion(val insert: String, val hint: String, val iconFile: java.nio.file.Path? = null)

// PSI-aware автодополнение — портировано по духу LogcatFilterCompletionContributor.kt
// (JetBrains/android, Apache-2.0): у реального контрибьютора 3 разных PSI-контекста
// (VALUE = позиция ключа, KVALUE = значение level:/is:/age:, STRING_KVALUE/REGEX_KVALUE =
// значение message:/package:/tag:/process:/line:), и подсказки зависят от того, В КАКОМ
// из них находится курсор — а не просто "последнее слово после пробела".

private enum class CompletionKind { KEY, LEVEL, IS, AGE, TAG, PACKAGE, PROCESS, APP, NONE }

private data class CompletionContext(val kind: CompletionKind, val prefix: String, val replaceStart: Int, val replaceEnd: Int)

private val TOKEN_DELIMITERS = charArrayOf(' ', '(', ')', '&', '|', '!')

// Ключи и подсказки взяты дословно из реального LogcatFilterCompletionContributor.kt /
// LogcatBundle.properties (JetBrains/android, Apache-2.0) — не придуманы заново.
private fun stringKeySuggestions(key: String, hint: String): List<FilterSuggestion> = listOf(
  FilterSuggestion("$key:", "$hint contains string"),
  FilterSuggestion("-$key:", "$hint does not contain string"),
  FilterSuggestion("$key~:", "$hint matches string"),
  FilterSuggestion("-$key~:", "$hint does not match string"),
  FilterSuggestion("$key=:", "$hint is exactly string"),
  FilterSuggestion("-$key=:", "$hint is not exactly string")
)

private val FILTER_SUGGESTIONS = listOf(
  FilterSuggestion("age:", "Filter by max age"),
  FilterSuggestion("is:", "Filter by type of log message"),
  FilterSuggestion("level:", "Filter by min log level"),
  FilterSuggestion("name:", "Specify a name for this filter"),
  FilterSuggestion("package:mine", "Filter logs from current project id(s)"),
  // "app:" — своя фича LogCactus (пикер приложений устройства с иконками),
  // ведёт себя как "package:", но с более удобным UI выбора.
  FilterSuggestion("app:", "Pick an installed app by icon (same as package:)"),
  FilterSuggestion("-app:", "Exclude an installed app (same as -package:)")
) + stringKeySuggestions("message", "Log message") +
  stringKeySuggestions("package", "Package name") +
  stringKeySuggestions("tag", "Log tag") +
  stringKeySuggestions("process", "Process name") +
  stringKeySuggestions("line", "Log line")

// Базовые ключи без модификаторов — то, что реально показывает Ctrl+Space на
// пустом поле (аналог KEY_LOOKUPS в LogcatFilterCompletionContributor.kt).
private val BASE_FILTER_SUGGESTIONS = listOf(
  FilterSuggestion("tag:", "Log tag contains string"),
  FilterSuggestion("age:", "Filter by max age"),
  FilterSuggestion("is:", "Filter by type of log message"),
  FilterSuggestion("level:", "Filter by min log level"),
  FilterSuggestion("line:", "Log line contains string"),
  FilterSuggestion("message:", "Log message contains string"),
  FilterSuggestion("name:", "Specify a name for this filter"),
  FilterSuggestion("package:", "Package name contains string"),
  FilterSuggestion("package:mine", "Filter logs from current project id(s)"),
  FilterSuggestion("process:", "Process name contains string"),
  FilterSuggestion("app:", "Pick an installed app by icon (same as package:)")
)

// Значения level:/is: — дословные hint-шаблоны ("Filter by X or higher",
// "Filter level X only") из LogcatBundle.properties.
private val LEVEL_NAMES = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT")
private val LEVEL_VALUE_SUGGESTIONS = LEVEL_NAMES.map { FilterSuggestion("$it ", "Filter by $it or higher") }
private val IS_VALUE_SUGGESTIONS = listOf(
  FilterSuggestion("crash ", "Filter crashes"),
  FilterSuggestion("firebase ", "Filter messages produced by Firebase libraries"),
  FilterSuggestion("stacktrace ", "Filter stack traces")
) + LEVEL_NAMES.map { FilterSuggestion("${it.lowercase()} ", "Filter level $it only") }
private val AGE_VALUE_SUGGESTIONS = listOf(
  FilterSuggestion("30s ", "Filter logs from past 30 second"),
  FilterSuggestion("5m ", "Filter logs from past 5 minute"),
  FilterSuggestion("3h ", "Filter logs from past 3 hour"),
  FilterSuggestion("1d ", "Filter logs from past 1 day")
)

/** Определяет, в каком "грамматическом" контексте находится курсор — ключ или значение конкретного ключа. */
/** Грузит иконку приложения (см. AppCatalog) с диска в Compose Painter. Кэшируется по пути файла. */
@Composable
private fun rememberFileIconPainter(path: java.nio.file.Path?): Painter? {
  if (path == null) return null
  val bitmap = remember(path) {
    runCatching {
      java.io.FileInputStream(path.toFile()).use { stream -> loadImageBitmap(stream) }
    }.getOrNull()
  }
  return bitmap?.let { BitmapPainter(it) }
}

private fun completionContextAt(text: String, cursor: Int): CompletionContext {
  var start = cursor
  while (start > 0 && text[start - 1] !in TOKEN_DELIMITERS) start--
  val token = text.substring(start, cursor)
  val colon = token.indexOf(':')
  if (colon < 0) {
    return CompletionContext(CompletionKind.KEY, token, start, cursor)
  }
  var key = token.substring(0, colon).lowercase()
  if (key.startsWith("-")) key = key.substring(1)
  if (key.endsWith("~") || key.endsWith("=")) key = key.dropLast(1)
  val kind = when (key) {
    "level" -> CompletionKind.LEVEL
    "is" -> CompletionKind.IS
    "age" -> CompletionKind.AGE
    "tag" -> CompletionKind.TAG
    "package" -> CompletionKind.PACKAGE
    "process" -> CompletionKind.PROCESS
    "app" -> CompletionKind.APP
    else -> CompletionKind.NONE
  }
  val valueStart = start + colon + 1
  return CompletionContext(kind, text.substring(valueStart, cursor), valueStart, cursor)
}

private fun suggestionsFor(ctx: CompletionContext, ui: ComposeUiBridge, forceAll: Boolean): List<FilterSuggestion> {
  fun List<String>.asSuggestions() = filter { it.startsWith(ctx.prefix, ignoreCase = true) }.map { FilterSuggestion("$it ", "") }
  return when (ctx.kind) {
    CompletionKind.KEY ->
      if (ctx.prefix.isEmpty()) {
        if (forceAll) BASE_FILTER_SUGGESTIONS else emptyList()
      } else {
        FILTER_SUGGESTIONS.filter { it.insert.startsWith(ctx.prefix, ignoreCase = true) && it.insert != ctx.prefix }
      }
    CompletionKind.LEVEL -> LEVEL_VALUE_SUGGESTIONS.filter { it.insert.startsWith(ctx.prefix, ignoreCase = true) }
    CompletionKind.IS -> IS_VALUE_SUGGESTIONS.filter { it.insert.startsWith(ctx.prefix, ignoreCase = true) }
    CompletionKind.AGE -> AGE_VALUE_SUGGESTIONS.filter { it.insert.startsWith(ctx.prefix, ignoreCase = true) }
    CompletionKind.TAG -> ui.knownTags.asSuggestions()
    CompletionKind.PACKAGE -> {
      val mine = if ("mine".startsWith(ctx.prefix, ignoreCase = true)) {
        listOf(FilterSuggestion("mine ", "Filter logs from current project id(s)"))
      } else {
        emptyList()
      }
      mine + ui.knownPackages.asSuggestions()
    }
    CompletionKind.PROCESS -> ui.knownProcesses.asSuggestions()
    CompletionKind.APP -> ui.knownApps
      .filter { it.label.startsWith(ctx.prefix, ignoreCase = true) || it.packageName.startsWith(ctx.prefix, ignoreCase = true) }
      .sortedBy { it.label.lowercase() }
      .map { FilterSuggestion("${it.packageName} ", it.label, it.iconFile) }
    CompletionKind.NONE -> emptyList()
  }
}

// Подсветка синтаксиса фильтра — дословные цвета из реальной схемы
// (LOGCAT_FILTER_KEY/KVALUE/STRING_KVALUE/REGEX_KVALUE в LogcatColorSchemeDarcula.xml,
// JetBrains/android, Apache-2.0): fg #fefefefe, bg #4D694C — распознанный
// key:value целиком подсвечивается зелёным. В реальной схеме нет отдельного
// "цвета ошибки" для Logcat-фильтра — там это платформенный error-annotator
// (LogcatFilterErrorAnnotator), поэтому для невалидных значений level:/is:/age:
// используем обычное подчёркивание ошибки, а не выдуманный цвет.
private val FILTER_TOKEN_BG = Color(0xFF4D694C)
private val FILTER_TOKEN_FG = Color(0xFFFEFEFE)
private val FILTER_ERROR_COLOR = Color(0xFFFF6B68)

private data class FilterToken(val range: IntRange, val key: String, val value: String, val valid: Boolean)

private fun isValidFilterValue(key: String, value: String): Boolean {
  if (value.isEmpty()) return true
  return when (key) {
    "level" -> LEVEL_NAMES.any { it.equals(value, ignoreCase = true) }
    "is" -> value.lowercase() in setOf("crash", "firebase", "stacktrace") || LEVEL_NAMES.any { it.equals(value, ignoreCase = true) }
    "age" -> {
      val suffix = value.last()
      val numPart = if (suffix in "smhd") value.dropLast(1) else value
      numPart.toLongOrNull() != null
    }
    else -> true
  }
}

private fun tokenizeFilterQuery(text: String): List<FilterToken> {
  val tokens = mutableListOf<FilterToken>()
  var i = 0
  while (i < text.length) {
    val c = text[i]
    if (c.isWhitespace() || c in "!&|()") {
      i++
      continue
    }
    if (c == '"') {
      val end = text.indexOf('"', i + 1)
      i = if (end < 0) text.length else end + 1
      continue
    }
    val start = i
    while (i < text.length && !text[i].isWhitespace() && text[i] !in "!&|()") i++
    val word = text.substring(start, i)
    val colon = word.indexOf(':')
    if (colon > 0) {
      var key = word.substring(0, colon).lowercase()
      if (key.startsWith("-")) key = key.substring(1)
      if (key.endsWith("~") || key.endsWith("=")) key = key.dropLast(1)
      if (key in KNOWN_FILTER_KEYS) {
        val value = word.substring(colon + 1)
        tokens.add(FilterToken(start until i, key, value, isValidFilterValue(key, value)))
      }
    }
  }
  return tokens
}

private val KNOWN_FILTER_KEYS =
  setOf("tag", "message", "package", "process", "line", "level", "is", "age", "name")

private class FilterSyntaxVisualTransformation(private val selection: TextRange = TextRange.Zero) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val styled = buildAnnotatedString {
      append(text)
      tokenizeFilterQuery(text.text).forEach { token ->
        val start = token.range.first
        val end = token.range.last + 1
        if (token.valid) {
          addStyle(SpanStyle(color = FILTER_TOKEN_FG), start, end)
          // Фон токена рисуется поверх системного выделения и полностью его перекрывал —
          // поэтому красим фоном только части токена вне выделенного диапазона.
          if (selection.collapsed) {
            addStyle(SpanStyle(background = FILTER_TOKEN_BG), start, end)
          } else {
            if (start < selection.min) addStyle(SpanStyle(background = FILTER_TOKEN_BG), start, minOf(end, selection.min))
            if (end > selection.max) addStyle(SpanStyle(background = FILTER_TOKEN_BG), maxOf(start, selection.max), end)
          }
        } else {
          addStyle(
            SpanStyle(color = FILTER_ERROR_COLOR, textDecoration = TextDecoration.Underline),
            start,
            end
          )
        }
      }
    }
    return TransformedText(styled, OffsetMapping.Identity)
  }
}

// Вынесено в отдельный composable: чтение списка строк (ui.hasVisibleRows) остаётся в его собственном scope
// и не перекомпонирует весь экран при каждом обновлении списка.
@Composable
private fun NoMatchBanner(ui: ComposeUiBridge) {
  if (ui.query.isNotBlank() && !ui.hasVisibleRows && ui.hasReceivedAnyLogs) {
    Row(
      modifier = Modifier.fillMaxWidth().background(WarningBannerBg).padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("No logs match the current filter", color = WarningBannerFg, fontSize = InputTextSize, modifier = Modifier.weight(1f))
      Text(
        "Clear filter",
        color = Accent,
        fontSize = InputTextSize,
        modifier = Modifier.clickable { ui.query = ""; ui.applyFilters(addToHistory = false) }
      )
    }
  }
}

@Composable
fun LogcatComposeTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colors = darkColors(
      primary = Accent,
      secondary = Accent,
      background = UiBg,
      surface = ToolbarBg,
      onPrimary = Color.White,
      onBackground = TextMain,
      onSurface = TextMain
    ),
    // Единая шкала шрифтов: 13 — основной текст и пункты меню, 12 — вторичный, 11 — подсказки, 10 — заголовки секций.
    // Без этого Text без явного размера брал 14sp Material, а кнопки — 14sp жирным с разрядкой (разнобой в панелях).
    typography = androidx.compose.material.Typography(
      h6 = TextStyle(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
      subtitle1 = TextStyle(fontSize = 13.sp),
      subtitle2 = TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
      body1 = TextStyle(fontSize = 13.sp),
      body2 = TextStyle(fontSize = 12.sp),
      button = TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 0.sp),
      caption = TextStyle(fontSize = 11.sp),
      overline = TextStyle(fontSize = 10.sp, letterSpacing = 0.8.sp)
    ),
    content = content
  )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogcatComposeScreen(ui: ComposeUiBridge) {
  val listState = rememberLazyListState()
  // Единый горизонтальный сдвиг всего списка (когда soft-wrap выключен): применяется ко всем
  // строкам одинаково (Modifier.horizontallyShifted), ввод колесом/тачпадом ловит один scrollable
  // на контейнере списка. Так скролл двигает весь контент целиком, а не только "длинные" строки.
  var horizontalOffsetPx by remember { mutableStateOf(0) }
  // Ширина области сообщений (без гуттера с бейджем) — по ней считается максимум сдвига.
  var messageViewportPx by remember { mutableStateOf(0) }
  // Монотонно растущий максимум натуральной ширины среди отрисованных строк —
  // основа стабильного скроллбара (см. StableHorizontalScrollbarAdapter ниже).
  var maxContentWidthPx by remember { mutableStateOf(0) }
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()
  var historyExpanded by remember { mutableStateOf(false) }

  // Cmd+F/Ctrl+F — see комментарий у SearchMatch выше.
  val rootFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  var searchOpen by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var searchCaseSensitive by remember { mutableStateOf(false) }
  var searchWholeWord by remember { mutableStateOf(false) }
  var searchRegexMode by remember { mutableStateOf(false) }
  var searchCurrentMatch by remember { mutableStateOf(0) } // 1-based, 0 = нет совпадений
  LaunchedEffect(Unit) { runCatching { rootFocusRequester.requestFocus() } }
  LaunchedEffect(searchOpen) { if (searchOpen) runCatching { searchFieldFocusRequester.requestFocus() } }

  // Чтение ui.visibleRows на верхнем уровне подписывает на него весь экран: каждое обновление
  // списка логов перекомпонировало тулбар и остальное. Поэтому читаем его, только когда поиск открыт.
  val searchRows = if (searchOpen && !ui.canvasMode) ui.visibleRows else emptyList()
  // Canvas-режим: строки списка — ui.canvasItems (читаем только при открытом поиске, как и visibleRows выше).
  val searchCanvasItems = if (searchOpen && ui.canvasMode) ui.canvasItems else null
  val searchMatches = remember(searchOpen, searchQuery, searchCaseSensitive, searchWholeWord, searchRegexMode, searchRows, searchCanvasItems) {
    if (!searchOpen || searchQuery.isEmpty()) emptyList()
    else if (searchCanvasItems != null) {
      buildSearchRegex(searchQuery, searchCaseSensitive, searchWholeWord, searchRegexMode)?.let { ui.canvasSearchMatches(it, searchCanvasItems) }.orEmpty()
    } else computeSearchMatches(searchRows, searchQuery, searchCaseSensitive, searchWholeWord, searchRegexMode)
  }
  LaunchedEffect(searchMatches) {
    if (ui.canvasMode) return@LaunchedEffect // у Canvas свой эффект ниже: не прыгаем к первому совпадению на каждую новую строку лога
    searchCurrentMatch = if (searchMatches.isEmpty()) 0 else 1
    searchMatches.firstOrNull()?.let { listState.animateScrollToItem(it.rowIndex) }
  }
  if (ui.canvasMode) {
    // Параметры поиска поменялись — к первому совпадению (один раз, а не при каждом обновлении списка).
    LaunchedEffect(searchOpen, searchQuery, searchCaseSensitive, searchWholeWord, searchRegexMode) {
      searchCurrentMatch = 0
    }
    LaunchedEffect(searchCurrentMatch == 0 && searchMatches.isNotEmpty(), searchQuery, searchCaseSensitive, searchWholeWord, searchRegexMode) {
      if (searchMatches.isNotEmpty() && searchCurrentMatch == 0) {
        searchCurrentMatch = 1
        ui.scrollCanvasToItem(searchMatches.first().rowIndex)
      }
    }
    // Подсветка для Canvas: по строкам, текущее совпадение отдельным цветом.
    val currentCanvasMatch = searchMatches.getOrNull(searchCurrentMatch - 1)
    LaunchedEffect(searchMatches, currentCanvasMatch, searchOpen) {
      ui.canvasSearchHighlight = if (searchOpen && searchMatches.isNotEmpty()) ComposeUiBridge.CanvasHighlight(searchMatches.groupBy { it.rowIndex }, currentCanvasMatch) else null
    }
  }
  fun searchStep(forward: Boolean) {
    if (searchMatches.isEmpty()) return
    searchCurrentMatch = when {
      forward && searchCurrentMatch >= searchMatches.size -> 1
      forward -> searchCurrentMatch + 1
      searchCurrentMatch <= 1 -> searchMatches.size
      else -> searchCurrentMatch - 1
    }
    val target = searchMatches.getOrNull(searchCurrentMatch - 1) ?: return
    if (ui.canvasMode) ui.scrollCanvasToItem(target.rowIndex) else coroutineScope.launch { listState.animateScrollToItem(target.rowIndex) }
  }

  fun goToStackFrame(forward: Boolean) {
    if (ui.canvasMode) { ui.jumpToStackFrameCanvas(forward); return }
    val rows = ui.visibleRows
    if (rows.isEmpty()) return
    val current = listState.firstVisibleItemIndex
    val target = if (forward) {
      (current + 1 until rows.size).firstOrNull { isStackFrameRow(rows[it]) }
        ?: (0..current).firstOrNull { isStackFrameRow(rows[it]) }
    } else {
      (current - 1 downTo 0).firstOrNull { isStackFrameRow(rows[it]) }
        ?: (rows.size - 1 downTo current).firstOrNull { isStackFrameRow(rows[it]) }
    }
    if (target != null) {
      coroutineScope.launch { listState.animateScrollToItem(target) }
    }
  }

  // Пока идёт поиск по тексту, свёртка стектрейсов выключена (иначе совпадения внутри кадров не найти).
  LaunchedEffect(searchOpen, searchQuery.isNotEmpty()) { ui.foldBypassForSearch = searchOpen && searchQuery.isNotEmpty() }

  fun goToBookmark(forward: Boolean) {
    if (ui.canvasMode) { ui.jumpToBookmarkCanvas(forward); return }
    val target = ui.findBookmarkRow(listState.firstVisibleItemIndex, forward)
    if (target < 0) {
      ui.showNotice(if (ui.bookmarks.isEmpty()) "No bookmarks: click a level badge to add one" else "Bookmarks are outside the current filter")
      return
    }
    ui.follow = false
    coroutineScope.launch { listState.animateScrollToItem(target) }
  }

  // Оба эффекта читают ui.visibleRows через snapshotFlow, а не в теле композиции, — иначе
  // весь экран (тулбар, кнопки) перекомпонировался бы на каждое обновление списка логов.
  LaunchedEffect(Unit) {
    snapshotFlow { !ui.hasVisibleRows }.collect { empty ->
      // Список очищен/отфильтрован в пустоту — старый максимум ширины больше не
      // актуален, иначе скроллбар показывал бы "есть куда крутить" на пустом списке.
      if (empty) maxContentWidthPx = 0
    }
  }

  // Ключ — id последней строки, а не размер списка: при полном буфере размер постоянен
  // (окно скользит), а LazyColumn с key держит позицию по элементу, а не по индексу —
  // без этого автоскролл к концу переставал срабатывать. Частоту обновлений ограничивает
  // цикл рендера в ComposeUiBridge.
  // Сдвиг не должен пережить переключение переноса строк и уменьшение ширины контента/увеличение окна.
  LaunchedEffect(ui.wrapLines) { horizontalOffsetPx = 0 }
  LaunchedEffect(maxContentWidthPx, messageViewportPx) {
    horizontalOffsetPx = horizontalOffsetPx.coerceIn(0, (maxContentWidthPx - messageViewportPx).coerceAtLeast(0))
  }

  LaunchedEffect(ui.follow) {
    if (!ui.follow) return@LaunchedEffect
    snapshotFlow { ui.visibleRows.lastOrNull()?.id }.collect {
      val rows = ui.visibleRows
      if (rows.isEmpty()) return@collect
      val targetIndex = rows.lastIndex
      val indexDelta = targetIndex - listState.firstVisibleItemIndex
      if (indexDelta <= 0) return@collect
      if (abs(indexDelta) <= 8) {
        listState.animateScrollToItem(targetIndex)
      } else {
        listState.scrollToItem(targetIndex)
      }
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().background(UiBg).padding(4.dp)
      .focusRequester(rootFocusRequester)
      .focusable()
      .onPreviewKeyEvent { event ->
        // Root — предок фокусируемого элемента в любом состоянии экрана, так
        // что этот обработчик видит Cmd+F/Escape ПЕРВЫМ (preview-фаза идёт
        // сверху вниз), независимо от того, где именно сейчас фокус (поле
        // фильтра, поиск, нигде) — глобальный шорткат, как в реальной IDE.
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          (event.isMetaPressed || event.isCtrlPressed) && event.key == Key.F -> {
            if (searchOpen) runCatching { searchFieldFocusRequester.requestFocus() } else searchOpen = true
            true
          }
          event.key == Key.Escape && searchOpen -> {
            searchOpen = false
            true
          }
          event.key == Key.F2 -> {
            goToBookmark(forward = !event.isShiftPressed)
            true
          }
          (event.isMetaPressed || event.isCtrlPressed) && event.isShiftPressed && event.key == Key.C -> {
            ui.copyBugReport { clipboardManager.setText(AnnotatedString(it)) }
            true
          }
          else -> false
        }
      },
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    DeviceDialogs(ui)
    Row(
      modifier = Modifier.fillMaxWidth().background(ToolbarBg).padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      DeviceDropdown(
        items = ui.devices,
        selected = ui.selectedDevice,
        onSelect = { ui.selectDevice(it) },
        width = 320.dp
      )
      var helpExpanded by remember { mutableStateOf(false) }
      var filterFieldFocused by remember { mutableStateOf(false) }
      // Видимость подсказок раньше зависела от filterFieldFocused, а фокус в
      // Compose Desktop хрупкий: клик по ЛЮБОЙ соседней кнопке в этом Row (X,
      // star, help) роняет его без надёжного восстановления — подсказки
      // переставали появляться насовсем. Теперь видимость — отдельный флаг,
      // не завязанный на фокус: сбрасывается при смене контекста completion
      // (новая позиция курсора/токен) и взводится только явным закрытием
      // меню (onDismissRequest) или Escape.
      var suggestionsDismissed by remember { mutableStateOf(false) }
      var forceShowAllSuggestions by remember { mutableStateOf(false) }
      var queryField by remember { mutableStateOf(TextFieldValue(ui.query)) }
      val queryFieldFocusRequester = remember { FocusRequester() }
      // ui.query может меняться извне (история, X, star) — держим локальное
      // TextFieldValue (курсор/выделение) в синхроне, не трогая курсор без нужды.
      LaunchedEffect(ui.query) {
        if (queryField.text != ui.query) queryField = TextFieldValue(ui.query, selection = TextRange(ui.query.length))
      }
      // Живая фильтрация по мере ввода с debounce ~100мс — как в реальном
      // Logcat (LogcatHeaderPanel.onFilterUpdate(BUFFER_RELOAD_DELAY)). В историю
      // при этом НЕ пишем — туда попадает только по Enter/потере фокуса/star.
      LaunchedEffect(queryField.text) {
        delay(100)
        ui.applyFilters(addToHistory = false)
      }
      val completionCtx = remember(queryField.text, queryField.selection) {
        completionContextAt(queryField.text, queryField.selection.end)
      }
      LaunchedEffect(completionCtx.kind) {
        if (completionCtx.kind == CompletionKind.APP) ui.loadAppsIfNeeded()
      }
      LaunchedEffect(completionCtx) {
        suggestionsDismissed = false
      }
      val suggestions = remember(
        completionCtx,
        forceShowAllSuggestions,
        ui.knownTags,
        ui.knownPackages,
        ui.knownProcesses,
        ui.knownApps
      ) {
        suggestionsFor(completionCtx, ui, forceShowAllSuggestions)
      }
      // Индекс подсветки для навигации стрелками — сбрасывается на 0 при
      // каждой смене набора подсказок, иначе указывал бы "в никуда" после
      // того как список отфильтровался по новому вводу.
      var selectedSuggestionIndex by remember { mutableStateOf(0) }
      LaunchedEffect(suggestions) {
        selectedSuggestionIndex = 0
      }
      // Не завязано на filterFieldFocused: suggestionsFor() и так возвращает
      // emptyList() на пустом query без forceShowAllSuggestions, так что
      // случайный показ на старте/после ухода фокуса ей не грозит, а
      // onDismissRequest ниже уже сам ловит клик вне поля независимо от
      // фокуса (PopupProperties(focusable = false) не мешает этому).
      val showSuggestions = !suggestionsDismissed && suggestions.isNotEmpty()
      fun applySuggestion(suggestion: FilterSuggestion) {
        val text = queryField.text
        val newText = text.substring(0, completionCtx.replaceStart) + suggestion.insert + text.substring(completionCtx.replaceEnd)
        val newCursor = completionCtx.replaceStart + suggestion.insert.length
        queryField = TextFieldValue(newText, TextRange(newCursor))
        ui.query = newText
        // Клик по пункту в DropdownMenuItem иногда сбивает Compose-фокус с
        // текстового поля (даже у non-focusable попапа) — возвращаем его явно,
        // чтобы курсор и клавиатурный ввод остались в поле. Видимость
        // подсказок теперь НЕ зависит от filterFieldFocused (см. showSuggestions),
        // так что даже если возврат фокуса где-то не сработает, автодополнение
        // не "залипнет" выключенным.
        queryFieldFocusRequester.requestFocus()
      }
      Box(modifier = Modifier.weight(1f)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(30.dp)
          .background(FieldBg, RoundedCornerShape(4.dp))
          .border(1.dp, if (filterFieldFocused) Accent else FieldStroke, RoundedCornerShape(4.dp))
          .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // В реальной Studio клик по funnel-иконке открывает список ранее
        // использованных фильтров со счётчиком совпадений — та же история,
        // что и у часов справа от поля.
        Tip("Filter history") {
          Icon(
            painter = painterResource("logcactus/icons/toolbar/filter.svg"),
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.width(13.dp).height(13.dp)
              .clickable(enabled = ui.queryHistory.isNotEmpty()) { historyExpanded = true }
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
          if (queryField.text.isEmpty()) {
            Text("Press ^Space to see suggestions", color = TextMuted, fontSize = QueryTextSize)
          }
          BasicTextField(
            value = queryField,
            onValueChange = { queryField = it; ui.query = it.text; forceShowAllSuggestions = false },
            modifier = Modifier.fillMaxWidth()
              .focusRequester(queryFieldFocusRequester)
              .onFocusChanged { state ->
                // Потеря фокуса — один из реальных триггеров записи в историю
                // (FilterTextField.textField.focusLost -> addToHistory()).
                if (filterFieldFocused && !state.isFocused) ui.applyFilters(addToHistory = true)
                filterFieldFocused = state.isFocused
              }
              .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                  // Ctrl+Space — как в реальной Studio (^Space, см. плейсхолдер поля).
                  event.isCtrlPressed && event.key == Key.Spacebar -> {
                    forceShowAllSuggestions = true
                    true
                  }
                  // Стрелки вверх/вниз — навигация по открытому списку подсказок
                  // (пользователь заметил, что её не было вовсе).
                  event.key == Key.DirectionDown && showSuggestions -> {
                    selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size
                    true
                  }
                  event.key == Key.DirectionUp && showSuggestions -> {
                    selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestions.size) % suggestions.size
                    true
                  }
                  // Enter при открытом списке — применяет подсвеченную подсказку
                  // (как в реальной Studio/большинстве автодополнений); иначе —
                  // обычный триггер записи в историю (не только blur).
                  event.key == Key.Enter && showSuggestions -> {
                    applySuggestion(suggestions[selectedSuggestionIndex])
                    true
                  }
                  event.key == Key.Enter -> {
                    ui.applyFilters(addToHistory = true)
                    true
                  }
                  event.key == Key.Escape && showSuggestions -> {
                    suggestionsDismissed = true
                    true
                  }
                  else -> false
                }
              },
            textStyle = TextStyle(color = TextMain, fontSize = QueryTextSize, fontFamily = FontFamily.Monospace),
            singleLine = true,
            cursorBrush = SolidColor(Accent),
            visualTransformation = remember(queryField.selection) { FilterSyntaxVisualTransformation(queryField.selection) }
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        if (ui.mineActive && ui.followedPackage.isNotBlank()) {
          MinePackageChip(ui.followedPackage, ui.minePinned, onClick = { ui.toggleMinePin() })
          Spacer(modifier = Modifier.width(4.dp))
        }
        if (ui.query.isNotEmpty()) {
          SmallButton(
            icon = painterResource("logcactus/icons/toolbar/close.svg"),
            tooltip = "Clear filter",
            onClick = { queryField = TextFieldValue(""); ui.query = ""; ui.applyFilters(addToHistory = false) }
          )
        }
        // Звезда переключает "избранное" текущего запроса в истории — это НЕ
        // применяет фильтр (FavoriteButton.mouseClicked() в FilterTextField.kt,
        // фильтрация уже идёт живьём по мере ввода).
        val isCurrentFavorite = ui.query.isNotBlank() && ui.favoriteQueries.contains(ui.query.trim())
        SmallButton(
          icon = painterResource(if (isCurrentFavorite) "logcactus/icons/toolbar/favorite-filled.svg" else "logcactus/icons/toolbar/favorite-outline.svg"),
          onClick = { ui.toggleFavorite(ui.query) },
          tooltip = "Add / remove this filter from favorites",
          tint = Color.Unspecified
        )
        SmallButton(
          icon = painterResource("logcactus/icons/toolbar/help.svg"),
          tooltip = "Help: shortcuts, filter syntax, features",
          onClick = { helpExpanded = true }
        )
        if (helpExpanded) HelpDialog(onClose = { helpExpanded = false })
      }
      DropdownMenu(
        expanded = showSuggestions,
        onDismissRequest = { suggestionsDismissed = true },
        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
        // Ширина по самой широкой строке (wrap_content) в пределах min..max, а не фиксированные 460dp.
        modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 240.dp, max = 640.dp).heightIn(max = 360.dp)
      ) {
        val isAppPicker = completionCtx.kind == CompletionKind.APP
        suggestions.forEachIndexed { index, suggestion ->
          // Свой Row вместо DropdownMenuItem: у последнего зашит Material-минимум
          // высоты 48dp, из-за которого паддинг выглядел вдвое больше нужного
          // (пользователь заметил) — тут высота и отступы полностью свои.
          // Подсветка текущего пункта — для навигации стрелками (её раньше не было).
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(24.dp)
              .background(if (index == selectedSuggestionIndex) Accent.copy(alpha = 0.18f) else Color.Transparent)
              .clickable { applySuggestion(suggestion) }
              .onPointerEvent(PointerEventType.Enter) { selectedSuggestionIndex = index }
              .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (isAppPicker) {
              val iconPainter = rememberFileIconPainter(suggestion.iconFile)
              Box(modifier = Modifier.width(18.dp).height(18.dp), contentAlignment = Alignment.Center) {
                if (iconPainter != null) {
                  Image(painter = iconPainter, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
                } else {
                  Box(modifier = Modifier.width(14.dp).height(14.dp).background(FieldStroke, RoundedCornerShape(3.dp)))
                }
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                suggestion.hint,
                color = TextMain,
                fontSize = InputTextSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                suggestion.insert.trimEnd(),
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = InputTextSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp)
              )
            } else {
              Text(
                suggestion.insert.trimEnd(),
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontSize = InputTextSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                // Без фиксированной ширины: строка = текст + spacer + hint по содержимому
                // (fill = false — длинное значение ужимается эллипсисом, hint не вытесняется).
                modifier = Modifier.weight(1f, fill = false)
              )
              if (suggestion.hint.isNotBlank()) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                  suggestion.hint,
                  color = TextMuted,
                  fontSize = InputTextSize,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
      }
      Box {
        // Отдельной кнопки-часов больше нет — funnel-иконка в поле фильтра уже
        // открывает эту же историю (см. leadingIcon поля выше), дублирующая
        // кнопка была лишней.
        DropdownMenu(expanded = historyExpanded, onDismissRequest = { historyExpanded = false }, modifier = Modifier.width(420.dp)) {
          // Избранное — отдельной секцией сверху, с разделителем перед остальной
          // историей (FilterTextField.HistoryList: favorites + separator + rest).
          val favorites = ui.queryHistory.filter { ui.favoriteQueries.contains(it) }
          val rest = ui.queryHistory.filterNot { ui.favoriteQueries.contains(it) }
          @Composable
          fun historyRow(item: String) {
            val count = remember(item) { ui.countMatches(item) } // один раз при открытии меню, а не на каждом кадре
            val isFavorite = ui.favoriteQueries.contains(item)
            DropdownMenuItem(onClick = {
              historyExpanded = false
              queryField = TextFieldValue(item, selection = TextRange(item.length))
              ui.pickHistory(item)
            }) {
              Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallButton(
                  icon = painterResource(if (isFavorite) "logcactus/icons/toolbar/favorite-filled.svg" else "logcactus/icons/toolbar/favorite-outline.svg"),
                  onClick = { ui.toggleFavorite(item) },
                  tooltip = "Favorite",
                  tint = Color.Unspecified
                )
                Text(
                  item,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                Text(if (count > 99) "99+" else count.toString(), color = TextMuted, fontSize = InputTextSize)
                SmallButton(
                  icon = painterResource("logcactus/icons/toolbar/close.svg"),
                  tooltip = "Remove from history",
                  onClick = { ui.removeFromHistory(item) }
                )
              }
            }
          }
          favorites.forEach { historyRow(it) }
          if (favorites.isNotEmpty() && rest.isNotEmpty()) Divider(color = FieldStroke)
          rest.forEach { historyRow(it) }
          if (ui.queryHistory.isNotEmpty()) {
            Divider(color = FieldStroke)
            DropdownMenuItem(onClick = {
              ui.clearHistory()
              historyExpanded = false
            }) {
              Text("Clear history", color = TextMuted)
            }
          }
        }
      }
      Tip("Treat filter text as a regular expression") {
        Row(
          modifier = Modifier.clickable { ui.changeRegex(!ui.regex) }.padding(horizontal = 4.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          PanelCheckbox(ui.regex)
          Text("Regex", color = TextMuted, fontSize = ButtonTextSize)
        }
      }
      Tip("Match case") {
        Row(
          modifier = Modifier.clickable { ui.changeCaseSensitive(!ui.caseSensitive) }.padding(horizontal = 4.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          PanelCheckbox(ui.caseSensitive)
          Text("Cc", color = TextMuted, fontSize = ButtonTextSize)
        }
      }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      SideToolbar(
        ui = ui,
        onPreviousOccurrence = { goToStackFrame(forward = false) },
        onNextOccurrence = { goToStackFrame(forward = true) },
        onNextBookmark = { goToBookmark(forward = true) },
        onPreviousBookmark = { goToBookmark(forward = false) },
        onBugReport = { ui.copyBugReport { clipboardManager.setText(AnnotatedString(it)) } },
        onCrashGo = { g ->
          val i = if (ui.canvasMode) -2 else ui.crashRowIndex(g)
          when {
            ui.canvasMode -> ui.jumpToCrashCanvas(g)
            i < 0 -> ui.showNotice("That line is no longer in the buffer or is hidden by the filter")
            else -> {
              ui.follow = false
              coroutineScope.launch { listState.animateScrollToItem(i) }
            }
          }
        },
        onCrashCopy = { g -> ui.copyCrashReport(g) { clipboardManager.setText(AnnotatedString(it)) } }
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .border(1.dp, FieldStroke)
          .background(PanelBg)
          .onPointerEvent(PointerEventType.Press) { ui.beginSelection() }
          .onPointerEvent(PointerEventType.Release) { ui.endSelection() }
          .onPointerEvent(PointerEventType.Exit) { ui.endSelection() }
      ) {
        // Cmd+F/Ctrl+F find bar — стандартный IntelliJ editor find (см. SearchMatch
        // выше), не что-то отдельное для Logcat. Cc/W/.* — case sensitive/whole
        // word/regex, счётчик результатов, prev/next (переиспользуем те же SVG,
        // что и у Previous/NextOccurrence в боковом тулбаре), Escape/X закрывают.
        if (searchOpen) {
          Row(
            modifier = Modifier.fillMaxWidth()
              .background(SearchBarBg, RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painter = painterResource("logcactus/icons/toolbar/filter.svg"),
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.width(13.dp).height(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
              if (searchQuery.isEmpty() && searchRegexMode) {
                Text("Regex", color = TextMuted, fontSize = QueryTextSize)
              }
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth()
                  .focusRequester(searchFieldFocusRequester)
                  .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                      event.key == Key.Enter && event.isShiftPressed -> { searchStep(forward = false); true }
                      event.key == Key.Enter -> { searchStep(forward = true); true }
                      else -> false
                    }
                  },
                textStyle = TextStyle(color = TextMain, fontSize = QueryTextSize, fontFamily = FontFamily.Monospace),
                singleLine = true,
                cursorBrush = SolidColor(Accent)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            SearchToggleButton("Cc", active = searchCaseSensitive, onClick = { searchCaseSensitive = !searchCaseSensitive })
            Spacer(modifier = Modifier.width(2.dp))
            SearchToggleButton("W", active = searchWholeWord, onClick = { searchWholeWord = !searchWholeWord })
            Spacer(modifier = Modifier.width(2.dp))
            SearchToggleButton(".*", active = searchRegexMode, onClick = { searchRegexMode = !searchRegexMode })
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              if (searchMatches.size == 1) "1 result" else "${searchMatches.size} results",
              color = TextMuted,
              fontSize = InputTextSize,
              maxLines = 1,
              softWrap = false
            )
            Spacer(modifier = Modifier.width(4.dp))
            SmallButton(
              icon = painterResource("logcactus/icons/toolbar/occurrence-prev.svg"),
              tooltip = "Previous match (Shift+Enter)",
              onClick = { searchStep(forward = false) }
            )
            SmallButton(
              icon = painterResource("logcactus/icons/toolbar/occurrence-next.svg"),
              tooltip = "Next match (Enter)",
              onClick = { searchStep(forward = true) }
            )
            SmallButton(
              icon = painterResource("logcactus/icons/toolbar/close.svg"),
              tooltip = "Close search (Esc)",
              onClick = { searchOpen = false }
            )
          }
        }
        // Дословно из LogcatMainPanel.kt: pausedBanner (тот самый оранжевый баннер
        // "Logcat is paused" с самого первого референс-скрина этой сессии) и
        // noLogsBanner (isLogsMissing() — фильтр непустой, логи были, но не осталось
        // ни одной видимой строки — с кнопкой очистки фильтра).
        if (ui.paused) {
          Row(
            modifier = Modifier.fillMaxWidth().background(WarningBannerBg).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Logcat is paused", color = WarningBannerFg, fontSize = InputTextSize)
          }
        }
        NoMatchBanner(ui)
        val horizontalScrollable = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
          val max = (maxContentWidthPx - messageViewportPx).coerceAtLeast(0)
          val next = (horizontalOffsetPx + delta.toInt()).coerceIn(0, max)
          val used = next - horizontalOffsetPx
          horizontalOffsetPx = next
          used.toFloat()
        }
        Box(
          modifier = Modifier.weight(1f).padding(6.dp).scrollable(
            state = horizontalScrollable,
            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
            // true — как у ScrollState/horizontalScroll: положительная дельта увеличивает сдвиг (контент уезжает влево).
            reverseDirection = true,
            enabled = !ui.wrapLines
          )
        ) {
        // Правый клик роняет приложение: встроенный в Compose Desktop 1.6.11
        // right-click context menu у SelectionContainer/ContextMenuArea падает
        // с "IllegalStateException: SelectionLayout must not be empty" в
        // SelectionManager.contextMenuOpenAdjustment — известный баг фреймворка
        // (не LogCactus-специфичный код). Контекстное меню тут и не планировалось
        // (см. README — "Copy Line/Copy Message" сознательно не реализованы,
        // копирование через выделение + Cmd+C), поэтому просто отключаем именно
        // right-click-меню через LocalTextContextMenu — обычное
        // drag-выделение и Cmd+C продолжают работать как прежде.
        if (ui.canvasMode) {
          LogCanvasList(
            ui = ui,
            hOffset = { horizontalOffsetPx },
            onContentWidth = { maxContentWidthPx = it }, // Canvas сам ведёт максимум и сбрасывает его при очистке/смене вида
            onViewportWidth = { messageViewportPx = it }
          )
        } else {
        val defaultMenu = androidx.compose.foundation.LocalContextMenuRepresentation.current
        val noSelectionMenu = remember(defaultMenu) { NoSelectionContextMenuRepresentation(defaultMenu) }
        CompositionLocalProvider(
          LocalTextContextMenu provides NoOpTextContextMenu,
          androidx.compose.foundation.LocalContextMenuRepresentation provides noSelectionMenu
        ) {
        SelectionContainer {
          LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(ui.visibleRows, key = { _, line -> line.id }) { index, line ->
              ContextMenuArea(items = {
                rowContextMenuItems(
                  line = line,
                  clipboard = clipboardManager,
                  bookmarked = ui.isBookmarked(line.entry),
                  expandLabel = expandMenuLabel(line, ui.isDetailExpanded(line.entry)),
                  onToggleExpand = { ui.toggleExpanded(line) },
                  onToggleBookmark = { ui.toggleBookmark(line) },
                  onBugReport = { ui.copyBugReport(line) { clipboardManager.setText(AnnotatedString(it)) } },
                  onSearchGoogle = { query -> coroutineScope.launch(Dispatchers.IO) { openGoogleSearch(query) } },
                  onClearLogcat = ui::clearLogs
                )
              }) {
              // Явная высота строки: и текст, и бейдж привязаны к ней, поэтому бейдж
              // центрируется по первой строке при любом размере шрифта (раньше был 16dp
              // сверху при произвольной высоте текстовой строки — "съезжал" вверх).
              val rowLineHeight = (ui.logFontSize * 1.35f).sp
              val rowLineHeightDp = with(LocalDensity.current) { rowLineHeight.toDp() }
              val bookmarked = ui.isBookmarked(line.entry)
              Row(
                modifier = if (bookmarked) Modifier.background(Accent.copy(alpha = 0.16f)) else Modifier,
                verticalAlignment = Alignment.Top
              ) {
                // Клик по бейджу уровня — поставить/снять закладку (полоска слева = закладка).
                Box(
                  modifier = Modifier
                    .width(3.dp)
                    .height(rowLineHeightDp)
                    .then(if (bookmarked) Modifier.background(Accent) else Modifier)
                )
                Box(modifier = Modifier.height(rowLineHeightDp), contentAlignment = Alignment.Center) {
                  Box(
                    modifier = Modifier
                      .width(18.dp)
                      .height(16.dp)
                      .then(
                        // У дочерних строк раскрытия JSON/Intent значок уровня не повторяется.
                        if (line.fold?.kind == RowFold.Kind.DETAIL) Modifier
                        else Modifier.background(line.badgeBg, RoundedCornerShape(2.dp)).clickable { ui.toggleBookmark(line) }
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = if (line.fold?.kind == RowFold.Kind.DETAIL) "" else levelLetter(line.level),
                      color = line.badgeFg,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 11.sp,
                      lineHeight = 11.sp,
                      softWrap = false,
                      maxLines = 1
                    )
                  }
                }
                Spacer(modifier = Modifier.width(6.dp))
                val displayText = if (searchOpen && searchQuery.isNotEmpty()) {
                  highlightSearchMatches(line.text, index, searchMatches, searchMatches.getOrNull(searchCurrentMatch - 1))
                } else {
                  line.text
                }
                // Когда soft-wrap выключен, длинные строки раньше просто
                // визуально "вылезали" за пределы панели без возможности их
                // докрутить (TextOverflow.Visible без скролла) — заменено на
                // горизонтальный скролл (общий ScrollState, см. выше), гуттер
                // (бейдж уровня) при этом остаётся на месте.
                // Ссылки в сообщении (URL_TAG) открываются кликом; строка-заглушка стектрейса — кликом
                // раскрывается/сворачивается. Оба обработчика вешаются только на нужные строки.
                val hasLinks = remember(line.id) {
                  line.message.contains("://") && line.text.getStringAnnotations(ComposeUiBridge.URL_TAG, 0, line.text.length).isNotEmpty()
                }
                var textLayout by remember(line.id) { mutableStateOf<TextLayoutResult?>(null) }
                val isStackFold = line.fold?.kind == RowFold.Kind.STACK
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .then(if (ui.wrapLines) Modifier else Modifier.onSizeChanged { messageViewportPx = it.width }.horizontallyShifted { horizontalOffsetPx })
                    .then(if (isStackFold) Modifier.clickable { ui.toggleExpanded(line) } else Modifier)
                ) {
                  Text(
                    text = displayText,
                    onTextLayout = if (hasLinks) { { textLayout = it } } else { {} },
                    fontFamily = FontFamily.Monospace,
                    fontSize = ui.logFontSize.sp,
                    lineHeight = rowLineHeight,
                    style = androidx.compose.ui.text.TextStyle(
                      lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                      )
                    ),
                    color = line.color,
                    softWrap = ui.wrapLines,
                    maxLines = if (ui.wrapLines) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Clip,
                    // Внутри Modifier.horizontalScroll() эта Text измеряется с
                    // бесконечной шириной — onSizeChanged здесь видит её НАСТОЯЩУЮ
                    // (не обрезанную под viewport) ширину, отсюда и берём
                    // maxContentWidthPx для стабильного скроллбара.
                    modifier = (if (ui.wrapLines) Modifier else Modifier.onSizeChanged { size ->
                      if (size.width > maxContentWidthPx) maxContentWidthPx = size.width
                    }).then(
                      if (hasLinks) {
                        Modifier.pointerInput(line.id) {
                          detectTapGestures { pos ->
                            val layout = textLayout ?: return@detectTapGestures
                            val offset = layout.getOffsetForPosition(pos)
                            line.text.getStringAnnotations(ComposeUiBridge.URL_TAG, offset, offset).firstOrNull()
                              ?.let { openUrl(it.item) }
                          }
                        }
                      } else Modifier
                    )
                  )
                }
              }
              }
            }
          }
        }
        }
        }
        ui.notice?.let { text ->
          Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
              .background(Color(0xE62B2D30), RoundedCornerShape(4.dp))
              .border(1.dp, Accent, RoundedCornerShape(4.dp))
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(text, color = TextMain, fontSize = InputTextSize)
              ui.noticeAction?.let { action ->
                Text(action.label, color = Accent, fontSize = InputTextSize, modifier = Modifier.clickable { action.run() }.padding(2.dp))
              }
            }
          }
        }
        if (!ui.canvasMode) VerticalScrollbar(
          adapter = rememberScrollbarAdapter(listState),
          modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
        if (!ui.wrapLines) {
          // Видимый индикатор скролла к Modifier.horizontalScroll(horizontalScrollState)
          // выше — без него пользователь не видит, что строку вообще можно
          // докрутить (замечено пользователем). Собственный адаптер вместо
          // rememberScrollbarAdapter(horizontalScrollState) — см. StableHorizontalScrollbarAdapter:
          // обычный adapter схлопывал thumb при вертикальном скролле, когда
          // среди перерисованных строк попадалась короткая (тоже замечено пользователем).
          HorizontalScrollbar(
            adapter = remember {
              StableHorizontalScrollbarAdapter(
                offset = { horizontalOffsetPx },
                setOffset = { horizontalOffsetPx = it },
                maxContentWidth = { maxContentWidthPx },
                viewportWidth = { messageViewportPx }
              )
            },
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
          )
        }
        }
      }
    }
  }
}

// Порядок и состав повторяют createToolbarActions() в реальном LogcatMainPanel.kt
// (JetBrains/android, Apache-2.0): Clear, Pause, Restart, ScrollToTheEnd, ToggleSoftWraps,
// [Configure view / LogcatFormatAction], [Apply — добавка самого LogCactus, в оригинале нет,
// т.к. там фильтр применяется живьём по мере ввода].
@Composable
private fun SideToolbar(
  ui: ComposeUiBridge,
  onPreviousOccurrence: () -> Unit,
  onNextOccurrence: () -> Unit,
  onNextBookmark: () -> Unit,
  onPreviousBookmark: () -> Unit,
  onBugReport: () -> Unit,
  onCrashGo: (CrashGroup) -> Unit,
  onCrashCopy: (CrashGroup) -> Unit
) {
  var deviceMenuExpanded by remember { mutableStateOf(false) }
  var configureExpanded by remember { mutableStateOf(false) }
  var bookmarksExpanded by remember { mutableStateOf(false) }
  val bookmarkClipboard = androidx.compose.ui.platform.LocalClipboardManager.current
  var crashesExpanded by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .width(34.dp)
      .fillMaxSize()
      .background(ToolbarBg)
      .padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    IconToolButton(painterResource("logcactus/icons/toolbar/clear.svg"), tooltip = "Clear logcat", onClick = ui::clearLogs)
    IconToolButton(painterResource("logcactus/icons/toolbar/pause.svg"), tooltip = "Pause / resume log updates", onClick = ui::togglePause, active = ui.paused)
    IconToolButton(painterResource("logcactus/icons/toolbar/refresh.svg"), tooltip = "Restart the logcat stream", onClick = ui::restartStream)
    IconToolButton(
      painterResource("logcactus/icons/toolbar/follow-tail.svg"), tooltip = "Scroll to the end (follow new logs)",
      onClick = { ui.follow = !ui.follow },
      active = ui.follow
    )
    // Навигация по строкам стектрейса ("  at Foo.bar(File.java:1)"), как
    // Previous/NextOccurrenceToolbarAction в реальном Logcat — там же по счёту.
    IconToolButton(painterResource("logcactus/icons/toolbar/occurrence-prev.svg"), tooltip = "Previous stack frame", onClick = onPreviousOccurrence)
    IconToolButton(painterResource("logcactus/icons/toolbar/occurrence-next.svg"), tooltip = "Next stack frame", onClick = onNextOccurrence)
    // Закладки: клик по бейджу уровня в строке ставит/снимает, F2/Shift+F2 — переход.
    Box {
      IconToolButton(
        painterResource("logcactus/icons/toolbar/bookmark.svg"), tooltip = "Bookmarks: click a level badge to add, F2 / Shift+F2 to jump",
        onClick = { bookmarksExpanded = true },
        active = ui.bookmarks.isNotEmpty()
      )
      DropdownMenu(expanded = bookmarksExpanded, onDismissRequest = { bookmarksExpanded = false }) {
        MenuColumn(minWidth = 230.dp) {
          MenuEntry("Next bookmark", onClick = { bookmarksExpanded = false; onNextBookmark() }, hint = "F2")
          MenuEntry("Previous bookmark", onClick = { bookmarksExpanded = false; onPreviousBookmark() }, hint = "Shift+F2")
          Divider(color = FieldStroke, modifier = Modifier.padding(vertical = 4.dp))
          MenuEntry("Copy bug report from bookmarks", onClick = { bookmarksExpanded = false; ui.copyBugReportFromBookmarks { bookmarkClipboard.setText(AnnotatedString(it)) } }, enabled = ui.bookmarks.isNotEmpty())
          MenuEntry("Clear bookmarks (${ui.bookmarks.size})", onClick = { bookmarksExpanded = false; ui.clearBookmarks() }, enabled = ui.bookmarks.isNotEmpty())
        }
      }

    }
    IconToolButton(painterResource("logcactus/icons/toolbar/bug-report.svg"), tooltip = "Copy bug report (Cmd/Ctrl+Shift+C)", onClick = onBugReport)
    Box {
      IconToolButton(
        painterResource("logcactus/icons/toolbar/crash.svg"), tooltip = "Crashes and ANR detected in the log",
        onClick = { crashesExpanded = true },
        active = ui.crashGroups.isNotEmpty()
      )
      DropdownMenu(expanded = crashesExpanded, onDismissRequest = { crashesExpanded = false }) {
        CrashPanel(ui.crashGroups, onGo = { crashesExpanded = false; onCrashGo(it) }, onCopy = { crashesExpanded = false; onCrashCopy(it) })
      }
    }
    IconToolButton(
      painterResource("logcactus/icons/toolbar/wrap-toggle.svg"), tooltip = "Soft-wrap long lines",
      onClick = { ui.wrapLines = !ui.wrapLines },
      active = ui.wrapLines
    )
    Box {
      IconToolButton(painterResource("logcactus/icons/toolbar/device-physical.svg"), tooltip = "Device actions and sessions", onClick = { deviceMenuExpanded = true }, active = deviceMenuExpanded)
      DropdownMenu(expanded = deviceMenuExpanded, onDismissRequest = { deviceMenuExpanded = false }) {
        fun close(action: () -> Unit): () -> Unit = { deviceMenuExpanded = false; action() }
        MenuColumn(minWidth = 260.dp) {
          // Действия над приложением относятся к приложению на экране устройства (или выбранному в фильтре процессу).
          Text(
            "Acts on: ${ui.actionTargetLabel()}",
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp)
          )
          MenuSection("Connection")
          MenuEntry("Connect via Wi-Fi…", close { ui.wifiDialog = true })
          if (ui.selectedIsNetworkDevice) MenuEntry("Disconnect this Wi-Fi device", close(ui::wifiDisconnect))
          MenuSection("App")
          MenuEntry("Restart app", close(ui::restartApp))
          MenuEntry("Force stop app", close(ui::forceStopApp))
          MenuEntry("Clear app cache", close(ui::clearAppCache))
          MenuEntry("Clear app data…", close(ui::requestClearData), destructive = true)
          MenuEntry("Open deep link…", close { ui.deepLinkDialog = true })
          MenuEntry("App info", close(ui::showAppInfo))
          MenuSection("Capture")
          MenuEntry("Screenshot", close(ui::takeScreenshot))
          MenuSection("Sessions")
          MenuEntry("Save session…", close(ui::requestSaveSession))
          MenuEntry("Open session…", close(ui::openSession))
          MenuEntry("Compare with session…", close(ui::compareWithSession))
          MenuSection("AI agents")
          if (com.cactus.logcactus.mcp.McpRuntime.running) {
            MenuEntry("MCP server: on — turn off", close { com.cactus.logcactus.mcp.McpRuntime.setEnabled(false) })
            MenuEntry("Copy MCP URL (${com.cactus.logcactus.mcp.McpRuntime.url})", close {
              java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(com.cactus.logcactus.mcp.McpRuntime.url), null)
              ui.showNotice("MCP URL copied (read-only, localhost only)")
            })
          } else {
            MenuEntry("MCP server: off — turn on (read-only)", close {
              com.cactus.logcactus.mcp.McpRuntime.setEnabled(true)
              ui.showNotice(com.cactus.logcactus.mcp.McpRuntime.lastError ?: "MCP server: ${com.cactus.logcactus.mcp.McpRuntime.url}")
            })
            com.cactus.logcactus.mcp.McpRuntime.lastError?.let { Text(it, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp)) }
          }
        }
      }

    }
    Spacer(modifier = Modifier.height(8.dp))
    Box {
      IconToolButton(
        painterResource("logcactus/icons/toolbar/configure-view.svg"), tooltip = "Configure view: columns, process, level",
        onClick = { configureExpanded = true },
        active = configureExpanded
      )
      DropdownMenu(expanded = configureExpanded, onDismissRequest = { configureExpanded = false }) {
        ConfigureViewPanel(ui)
      }
    }
  }
}

// Панель крашей/ANR: одинаковые падения сгруппированы, клик — переход к последнему вхождению.
@Composable
private fun CrashPanel(groups: List<CrashGroup>, onGo: (CrashGroup) -> Unit, onCopy: (CrashGroup) -> Unit) {
  Column(modifier = Modifier.width(520.dp).heightIn(max = 380.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("Crashes & ANR", color = TextMain, fontSize = InputTextSize)
    if (groups.isEmpty()) {
      Text("Nothing detected yet (FATAL EXCEPTION, ANR, native signal).", color = TextMuted, fontSize = InputTextSize)
    }
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      groups.forEach { g ->
        Row(
          modifier = Modifier.fillMaxWidth().clickable { onGo(g) }.padding(vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            g.kind.name,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.background(if (g.kind == CrashKind.ANR) Color(0xFFBBB529).copy(alpha = 0.6f) else Color(0xFFCF5B56), RoundedCornerShape(3.dp))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(g.title, color = TextMain, fontSize = InputTextSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              "${g.packageName.ifBlank { "unknown process" }}  ·  last ${timeFormat.format(java.util.Date(g.lastSeen))}",
              color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
          }
          Text("×${g.count}", color = TextMain, fontSize = InputTextSize)
          Text("Copy", color = Accent, fontSize = InputTextSize, modifier = Modifier.clickable { onCopy(g) }.padding(4.dp))
        }
      }
    }
  }
}

// Последний размер больших окон (по заголовку) на время работы приложения: пользователь растянул — остаётся.
private object DialogSizes {
  private val sizes = HashMap<String, androidx.compose.ui.unit.DpSize>()
  fun get(key: String, default: androidx.compose.ui.unit.DpSize) = sizes[key] ?: default
  fun put(key: String, size: androidx.compose.ui.unit.DpSize) { if (size.width.value.isFinite() && size.height.value.isFinite()) sizes[key] = size }
}

// Большое окно (справка, сравнение сессий, Wi-Fi): отдельное окно ОС с фиксированным начальным размером, которое
// можно растягивать мышью. Раньше это был AlertDialog: его высота зависела от измерения прокручиваемого
// содержимого, диалог растягивался, а текст "прыгал" при прокрутке. В headless (тесты) окон ОС нет — встроенный Dialog.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LargeDialog(
  title: String,
  onClose: () -> Unit,
  width: androidx.compose.ui.unit.Dp = 720.dp,
  height: androidx.compose.ui.unit.Dp = 560.dp,
  actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
  content: @Composable () -> Unit
) {
  val body: @Composable () -> Unit = {
    Column(modifier = Modifier.fillMaxSize().background(ToolbarBg).padding(20.dp)) {
      Text(title, color = TextMain, fontSize = 18.sp)
      Spacer(modifier = Modifier.height(14.dp))
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
      Spacer(modifier = Modifier.height(10.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
  }
  // logcactus.inlineDialogs=true — тесты: окна ОС не создаём (на экране разработчика они были бы видны).
  if (java.awt.GraphicsEnvironment.isHeadless() || java.lang.Boolean.getBoolean("logcactus.inlineDialogs")) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
      Box(modifier = Modifier.width(width).height(height).clip(RoundedCornerShape(8.dp))) { body() }
    }
  } else {
    val state = androidx.compose.ui.window.rememberDialogState(
      position = androidx.compose.ui.window.WindowPosition(Alignment.Center),
      size = DialogSizes.get(title, androidx.compose.ui.unit.DpSize(width, height))
    )
    LaunchedEffect(state) { snapshotFlow { state.size }.collect { DialogSizes.put(title, it) } }
    androidx.compose.ui.window.DialogWindow(
      onCloseRequest = onClose,
      state = state,
      title = title,
      resizable = true,
      onPreviewKeyEvent = { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { onClose(); true } else false
      }
    ) { body() }
  }
}

// Подключение устройства по Wi-Fi: быстрый режим из USB, сопряжение по коду (Android 11+), подключение, поиск в сети.
@Composable
private fun WifiDialog(ui: ComposeUiBridge) {
  var pairAddress by remember { mutableStateOf("") }
  var pairCode by remember { mutableStateOf("") }
  var connectAddress by remember { mutableStateOf("") }

  @Composable
  fun field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(FieldBg, RoundedCornerShape(4.dp)).border(1.dp, FieldStroke, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
      if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = InputTextSize)
      BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = InputTextSize, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier.fillMaxWidth()
      )
    }
  }

  @Composable
  fun action(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    androidx.compose.material.TextButton(enabled = enabled && !ui.wifiBusy, onClick = onClick) { Text(label, color = if (enabled && !ui.wifiBusy) Accent else TextMuted) }

  val wifiScroll = rememberScrollState()
  LargeDialog(
    title = "Connect via Wi-Fi",
    onClose = { ui.wifiDialog = false },
    width = 680.dp,
    actions = { androidx.compose.material.TextButton(onClick = { ui.wifiDialog = false }) { Text("Close", color = Accent) } }
  ) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(wifiScroll).padding(end = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("1. Phone is connected by USB", color = Accent, fontSize = 13.sp)
            Text("Switches the selected USB device to network mode and connects to its Wi-Fi address. Both must be in the same network.", color = TextMuted, fontSize = InputTextSize)
            action("Enable Wi-Fi for ${ui.selectedDevice?.id ?: "the selected device"}", enabled = ui.selectedDevice != null && !ui.selectedIsNetworkDevice) { ui.wifiEnableFromUsb() }
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("2. Pair (Android 11+, no cable)", color = Accent, fontSize = 13.sp)
            Text("On the phone: Developer options → Wireless debugging → Pair device with pairing code. Enter the address and the 6-digit code it shows.", color = TextMuted, fontSize = InputTextSize)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              field(pairAddress, { pairAddress = it }, "192.168.0.12:44444", Modifier.weight(1f))
              field(pairCode, { pairCode = it.filter(Char::isDigit).take(6) }, "123456", Modifier.width(110.dp))
              action("Pair", enabled = pairAddress.isNotBlank() && pairCode.length == 6) { ui.wifiPair(pairAddress, pairCode) }
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("3. Connect", color = Accent, fontSize = 13.sp)
            Text("The IP address and port from the main Wireless debugging screen (or 5555 after step 1).", color = TextMuted, fontSize = InputTextSize)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              field(connectAddress, { connectAddress = it }, "192.168.0.12:37123", Modifier.weight(1f))
              action("Connect", enabled = connectAddress.isNotBlank()) { ui.wifiConnect(connectAddress) }
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Found on the network", color = Accent, fontSize = 13.sp)
              action("Scan") { ui.wifiScan() }
              if (ui.wifiBusy) Text("working…", color = TextMuted, fontSize = InputTextSize)
            }
            ui.wifiStatus?.let { Text(it, color = TextMuted, fontSize = InputTextSize) }
            if (ui.wifiServices.isEmpty()) {
              Text("Press Scan to look for phones with Wireless debugging turned on. Click a result to fill the field.", color = TextMuted, fontSize = InputTextSize)
            }
            ui.wifiServices.forEach { svc ->
              Row(
                modifier = Modifier.fillMaxWidth().clickable { if (svc.pairing) pairAddress = svc.hostPort else connectAddress = svc.hostPort }.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Text(if (svc.pairing) "pairing" else "connect", color = if (svc.pairing) Color(0xFFBBB529) else Color(0xFF6A8759), fontSize = InputTextSize, modifier = Modifier.width(64.dp))
                Text(svc.hostPort, color = TextMain, fontFamily = FontFamily.Monospace, fontSize = InputTextSize)
                Text(svc.name, color = TextMuted, fontSize = InputTextSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
    }
  }
}

// Справка по кнопке «?»: хоткеи, синтаксис фильтра, возможности. Текст выделяется и копируется.
@Composable
private fun HelpDialog(onClose: () -> Unit) {
  val clipboard = LocalClipboardManager.current
  val scrollState = rememberScrollState()
  LargeDialog(
    title = "LogCat by cactus — help",
    onClose = onClose,
    width = 820.dp,
    actions = {
      androidx.compose.material.TextButton(onClick = { clipboard.setText(AnnotatedString(HelpContent.asText())) }) { Text("Copy", color = Accent) }
      androidx.compose.material.TextButton(onClick = onClose) { Text("Close", color = Accent) }
    }
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp)) {
        SelectionContainer {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HelpContent.sections.forEach { section ->
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, color = Accent, fontSize = 13.sp)
                section.entries.forEach { entry ->
                  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.keys, color = TextMain, fontFamily = FontFamily.Monospace, fontSize = InputTextSize, modifier = Modifier.width(250.dp))
                    Text(entry.description, color = TextMuted, fontSize = InputTextSize, modifier = Modifier.weight(1f))
                  }
                }
              }
            }
          }
        }
      }
      VerticalScrollbar(adapter = rememberScrollbarAdapter(scrollState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
  }
}

// Диалоги действий над устройством: подтверждение необратимого, ввод deep link, сведения о приложении.
@Composable
private fun DeviceDialogs(ui: ComposeUiBridge) {
  ui.confirmRequest?.let { req ->
    androidx.compose.material.AlertDialog(
      onDismissRequest = { ui.confirmRequest = null },
      backgroundColor = ToolbarBg,
      contentColor = TextMain,
      title = { Text(req.title, color = TextMain) },
      text = { Text(req.message, color = TextMuted) },
      confirmButton = {
        androidx.compose.material.TextButton(onClick = { ui.confirmRequest = null; req.onConfirm() }) { Text(req.confirmLabel, color = Color(0xFFCF5B56)) }
      },
      dismissButton = { androidx.compose.material.TextButton(onClick = { ui.confirmRequest = null }) { Text("Cancel", color = TextMuted) } }
    )
  }
  if (ui.deepLinkDialog) {
    var uri by remember { mutableStateOf("") }
    androidx.compose.material.AlertDialog(
      onDismissRequest = { ui.deepLinkDialog = false },
      backgroundColor = ToolbarBg,
      contentColor = TextMain,
      title = { Text("Open deep link", color = TextMain) },
      text = {
        Box(modifier = Modifier.fillMaxWidth().background(FieldBg, RoundedCornerShape(4.dp)).border(1.dp, FieldStroke, RoundedCornerShape(4.dp)).padding(8.dp)) {
          if (uri.isEmpty()) Text("myapp://screen?id=1 or https://…", color = TextMuted, fontSize = QueryTextSize)
          BasicTextField(
            value = uri,
            onValueChange = { uri = it },
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = QueryTextSize, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        androidx.compose.material.TextButton(enabled = uri.isNotBlank(), onClick = { ui.deepLinkDialog = false; ui.openDeepLink(uri) }) { Text("Open", color = Accent) }
      },
      dismissButton = { androidx.compose.material.TextButton(onClick = { ui.deepLinkDialog = false }) { Text("Cancel", color = TextMuted) } }
    )
  }
  ui.saveRequest?.let { req ->
    var visibleOnly by remember(req) { mutableStateOf(true) }
    androidx.compose.material.AlertDialog(
      onDismissRequest = { ui.saveRequest = null },
      backgroundColor = ToolbarBg,
      contentColor = TextMain,
      title = { Text("Save session", color = TextMain) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          @Composable
          fun option(selected: Boolean, label: String, hint: String, onSelect: () -> Unit) {
            Row(
              modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              androidx.compose.material.RadioButton(selected = selected, onClick = onSelect)
              Column {
                Text(label, color = TextMain, fontSize = InputTextSize)
                Text(hint, color = TextMuted, fontSize = 11.sp)
              }
            }
          }
          option(visibleOnly, "Only what is visible (${req.visibleCount} lines)", "Lines that match the current filter, level and process", { visibleOnly = true })
          option(!visibleOnly, "All logs in the buffer (${req.allCount} lines)", "Everything received from the device, filters are ignored", { visibleOnly = false })
        }
      },
      confirmButton = {
        androidx.compose.material.TextButton(onClick = { ui.saveRequest = null; ui.saveSession(visibleOnly) }) { Text("Save…", color = Accent) }
      },
      dismissButton = { androidx.compose.material.TextButton(onClick = { ui.saveRequest = null }) { Text("Cancel", color = TextMuted) } }
    )
  }
  if (ui.wifiDialog) WifiDialog(ui)
  ui.compareReport?.let { report ->
    val clipboard = LocalClipboardManager.current
    // Один очень высокий Text рисовался с пустым куском (предел размера текстуры), поэтому текст порезан на
    // куски по COMPARE_CHUNK_LINES строк; прокрутка обычная (высота контента точная, скроллбар не прыгает).
    val chunks = remember(report) { report.lines().chunked(COMPARE_CHUNK_LINES).map { it.joinToString("\n") } }
    val scrollState = rememberScrollState()
    LargeDialog(
      title = "Session comparison",
      onClose = { ui.compareReport = null },
      actions = {
        androidx.compose.material.TextButton(onClick = { ui.compareReport = null }) { Text("Close", color = TextMuted) }
        androidx.compose.material.TextButton(onClick = { clipboard.setText(AnnotatedString(report)); ui.showNotice("Comparison copied") }) { Text("Copy", color = Accent) }
      }
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp)) {
          SelectionContainer {
            Column {
              chunks.forEach { chunk -> Text(chunk, color = TextMain, fontFamily = FontFamily.Monospace, fontSize = InputTextSize) }
            }
          }
        }
        VerticalScrollbar(adapter = rememberScrollbarAdapter(scrollState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
      }
    }
  }
  ui.infoDialog?.let { text ->
    val clipboard = LocalClipboardManager.current
    androidx.compose.material.AlertDialog(
      onDismissRequest = { ui.infoDialog = null },
      backgroundColor = ToolbarBg,
      contentColor = TextMain,
      title = { Text("App info", color = TextMain) },
      // SelectionContainer: текст можно выделять мышью и копировать произвольный фрагмент (Cmd/Ctrl+C).
      text = { SelectionContainer { Text(text, color = TextMain, fontFamily = FontFamily.Monospace, fontSize = InputTextSize) } },
      confirmButton = { androidx.compose.material.TextButton(onClick = { ui.infoDialog = null }) { Text("Close", color = Accent) } },
      dismissButton = {
        androidx.compose.material.TextButton(onClick = { clipboard.setText(AnnotatedString(text)); ui.showNotice("App info copied") }) { Text("Copy", color = Accent) }
      }
    )
  }
}

// Панель "Configure view": секции с единой сеткой — подпись слева, значение/степпер справа, строки одной высоты.
@Composable
private fun ConfigureViewPanel(ui: ComposeUiBridge) {
  Column(
    modifier = Modifier.width(PanelWidth).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    PanelSection("Process", first = true)
    SimpleDropdown(
      items = ui.processOptions,
      selected = ui.selectedProcess,
      label = { it.label },
      onSelect = { ui.selectProcess(it) },
      width = PanelWidth - 28.dp
    )
    PanelCheckRow("All processes", ui.allProcesses) { ui.toggleAllProcesses(it) }
    PanelRow("Minimum level") {
      SimpleDropdown(items = ui.levelOptions, selected = ui.level, label = { it }, onSelect = { ui.changeLevel(it) }, width = 120.dp)
    }

    PanelSection("Columns")
    PanelColumnRow("Time", ui.showTime, ui.timeWidth, { ui.changeShowTime(it) }, { ui.adjustWidth(0, -1) }, { ui.adjustWidth(0, 1) })
    PanelColumnRow("Tag", ui.showTag, ui.tagWidth, { ui.changeShowTag(it) }, { ui.adjustWidth(1, -1) }, { ui.adjustWidth(1, 1) })
    // Реальный Logcat разделяет appName (пакет) и processName (PID/имя процесса)
    // на два независимых toggle'а (FormattingOptions.appNameFormat/processNameFormat,
    // см. ai/logcat_v2_reference.md) — раньше у LogCactus это было смешано в одну "Process".
    PanelColumnRow("App name", ui.showAppName, ui.appNameWidth, { ui.changeShowAppName(it) }, { ui.adjustWidth(3, -1) }, { ui.adjustWidth(3, 1) })
    PanelColumnRow("Process", ui.showProcess, ui.processWidth, { ui.changeShowProcess(it) }, { ui.adjustWidth(2, -1) }, { ui.adjustWidth(2, 1) })

    PanelSection("Display")
    PanelCheckRow("Fold stack traces", ui.foldStacks) { ui.changeFoldStacks(it) }
    PanelCheckRow("Auto-expand JSON / Intent", ui.autoExpandJson) { ui.changeAutoExpandJson(it) }
    PanelCheckRow("Auto-expand long URLs", ui.autoExpandUrls) { ui.changeAutoExpandUrls(it) }
    PanelCheckRow("Hide duplicate tags", ui.hideDuplicateTags) { ui.changeHideDuplicateTags(it) }

    PanelSection("Text and history")
    PanelRow("Font size") {
      PanelStepper(ui.logFontSize.toString(), "Smaller font", "Larger font", { ui.adjustLogFontSize(-1) }, { ui.adjustLogFontSize(1) })
    }
    // Размер буфера логов: как "cycle buffer" в Logcat Android Studio. Старые строки выбрасываются с головы.
    PanelRow("Buffer") {
      PanelStepper(
        formatBufferSize(ui.bufferBytes),
        "Smaller buffer: older lines are dropped sooner",
        "Larger buffer: keeps more history, uses more memory",
        { ui.adjustBuffer(-1) },
        { ui.adjustBuffer(1) }
      )
    }
    Text("${ui.bufferLines} lines in the buffer now", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
  }
}

private val PanelWidth = 320.dp
private val PanelRowHeight = 30.dp

@Composable
private fun PanelSection(title: String, first: Boolean = false) {
  if (!first) {
    Spacer(modifier = Modifier.height(8.dp))
    Divider(color = FieldStroke)
  }
  Text(
    title.uppercase(),
    color = TextMuted,
    fontSize = 10.sp,
    letterSpacing = 0.8.sp,
    modifier = Modifier.padding(top = if (first) 0.dp else 8.dp, bottom = 4.dp)
  )
}

// Строка сетки: подпись слева, содержимое справа, единая высота.
@Composable
private fun PanelRow(label: String, content: @Composable () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().height(PanelRowHeight), verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = TextMain, fontSize = InputTextSize, modifier = Modifier.weight(1f))
    content()
  }
}

@Composable
private fun PanelCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(PanelRowHeight).clickable { onChange(!checked) },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    PanelCheckbox(checked)
    Text(label, color = TextMain, fontSize = InputTextSize)
  }
}

// Колонка лога: чекбокс + подпись слева, ширина (степпер) справа — степперы всех колонок в одной вертикали.
@Composable
private fun PanelColumnRow(
  title: String,
  checked: Boolean,
  width: Int,
  onChecked: (Boolean) -> Unit,
  onMinus: () -> Unit,
  onPlus: () -> Unit
) {
  Row(modifier = Modifier.fillMaxWidth().height(PanelRowHeight), verticalAlignment = Alignment.CenterVertically) {
    Row(
      modifier = Modifier.weight(1f).fillMaxHeight().clickable { onChecked(!checked) },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      PanelCheckbox(checked)
      Text(title, color = TextMain, fontSize = InputTextSize)
    }
    PanelStepper(width.toString(), "Narrower column", "Wider column", onMinus, onPlus)
  }
}

@Composable
private fun PanelStepper(value: String, minusTip: String, plusTip: String, onMinus: () -> Unit, onPlus: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    SmallButton(icon = painterResource("logcactus/icons/toolbar/minus.svg"), tooltip = minusTip, onClick = onMinus)
    Text(value, color = TextMain, fontSize = InputTextSize, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(54.dp))
    SmallButton(icon = painterResource("logcactus/icons/toolbar/plus.svg"), tooltip = plusTip, onClick = onPlus)
  }
}

// Свой чекбокс 16dp: у Material Checkbox минимальная область 48dp (scale(0.5) лишь уменьшал картинку, а место занимал
// полный размер — отсюда рваные отступы в панели).
@Composable
private fun PanelCheckbox(checked: Boolean) {
  Box(
    modifier = Modifier
      .size(16.dp)
      .clip(RoundedCornerShape(3.dp))
      .background(if (checked) Accent else Color.Transparent)
      .border(1.dp, if (checked) Accent else TextMuted, RoundedCornerShape(3.dp)),
    contentAlignment = Alignment.Center
  ) {
    if (checked) {
      androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(Color.White, Offset(size.width * 0.1f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.85f), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.92f, size.height * 0.15f), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
      }
    }
  }
}

private fun levelLetter(level: String): String = when (level) {
  "ERROR" -> "E"
  "ASSERT" -> "A"
  "WARN" -> "W"
  "INFO" -> "I"
  "DEBUG" -> "D"
  else -> "V"
}

private fun deviceLabel(device: DeviceRef?): String {
  if (device == null) return "-"
  if (device.androidVersion.isBlank() && device.apiLevel.isBlank()) return device.displayName
  val version = device.androidVersion.ifBlank { "?" }
  val api = device.apiLevel.ifBlank { "?" }
  return "${device.displayName}  Android $version, API $api"
}

@Composable
private fun DeviceDropdown(
  items: List<DeviceRef>,
  selected: DeviceRef?,
  onSelect: (DeviceRef) -> Unit,
  width: Dp
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    SelectField(
      text = deviceLabel(selected),
      width = width,
      onClick = { expanded = true },
      leading = {
        Icon(
          painter = painterResource(
            if (selected?.isEmulator == false) "logcactus/icons/toolbar/device-physical.svg" else "logcactus/icons/toolbar/device-virtual.svg"
          ),
          contentDescription = null,
          tint = TextMuted,
          modifier = Modifier.size(14.dp)
        )
      }
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      MenuColumn(minWidth = width) {
        items.forEach { item -> MenuEntry(deviceLabel(item), onClick = { expanded = false; onSelect(item) }) }
        if (items.isEmpty()) MenuEntry("No devices found", onClick = {}, enabled = false)
      }
    }
  }
}

@Composable
private fun <T> SimpleDropdown(
  items: List<T>,
  selected: T?,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  width: Dp
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    SelectField(text = selected?.let(label) ?: "-", width = width, onClick = { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      MenuColumn(minWidth = width) {
        items.forEach { item ->
          MenuEntry(label(item), onClick = { expanded = false; onSelect(item) })
        }
      }
    }
  }
}

private val FieldBgHover = Color(0xFF32363E)
internal val DangerColor = Color(0xFFE06C6C)

// Поле выбора (выпадающий список): текст слева, ▾ справа, подсветка при наведении. Вместо Material Button
// (у него текст по центру, жирный и с разрядкой — выглядело как кнопка, а не как список).
@Composable
private fun SelectField(text: String, width: Dp, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
  val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val hovered by source.collectIsHoveredAsState()
  Row(
    modifier = Modifier
      .width(width)
      .height(32.dp)
      .clip(RoundedCornerShape(4.dp))
      .background(if (hovered) FieldBgHover else FieldBg)
      .hoverable(source)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    leading?.invoke()
    Text(text, color = TextMain, fontSize = ButtonTextSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    // Шеврон рисуем сами: глиф "▾" в запасном шрифте получался крошечным.
    androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 10.dp, height = 6.dp)) {
      val stroke = 1.6.dp.toPx()
      drawLine(TextMuted, Offset(0f, 0f), Offset(size.width / 2, size.height), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
      drawLine(TextMuted, Offset(size.width / 2, size.height), Offset(size.width, 0f), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
  }
}

// Пункт меню: единая высота 32dp, отступы 14dp, подсветка при наведении, необязательная подсказка справа
// (горячая клавиша), "опасный" вариант для необратимых действий.
@Composable
internal fun MenuEntry(label: String, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false, hint: String? = null) {
  val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val hovered by source.collectIsHoveredAsState()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(32.dp)
      .background(if (hovered && enabled) Color.White.copy(alpha = 0.07f) else Color.Transparent)
      .hoverable(source)
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      label,
      color = when {
        !enabled -> TextMuted.copy(alpha = 0.5f)
        destructive -> DangerColor
        else -> TextMain
      },
      fontSize = ButtonTextSize,
      maxLines = 1,
      modifier = Modifier.weight(1f)
    )
    if (hint != null) {
      Spacer(modifier = Modifier.width(24.dp))
      Text(hint, color = TextMuted, fontSize = 11.sp)
    }
  }
}

// Заголовок секции меню (как в панели Configure view): мелкие заглавные, разделитель сверху.
@Composable
internal fun MenuSection(title: String, first: Boolean = false) {
  if (!first) {
    Spacer(modifier = Modifier.height(4.dp))
    Divider(color = FieldStroke)
  }
  Text(
    title.uppercase(),
    color = TextMuted,
    fontSize = 10.sp,
    letterSpacing = 0.8.sp,
    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp)
  )
}

// Контейнер меню: минимальная ширина и вертикальные отступы, одинаковые у всех меню.
@Composable
internal fun MenuColumn(minWidth: Dp = 240.dp, content: @Composable () -> Unit) {
  Column(modifier = Modifier.widthIn(min = minWidth).padding(vertical = 6.dp)) { content() }
}

// Подсказка при наведении (через ~0,5 с), как у кнопок IDE. null — без подсказки.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Tip(text: String?, content: @Composable () -> Unit) {
  if (text == null) {
    content()
    return
  }
  androidx.compose.foundation.TooltipArea(
    tooltip = {
      Box(
        modifier = Modifier
          .background(Color(0xFF3C3F41), RoundedCornerShape(4.dp))
          .border(1.dp, FieldStroke, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) { Text(text, color = TextMain, fontSize = InputTextSize) }
    },
    delayMillis = 500,
    tooltipPlacement = androidx.compose.foundation.TooltipPlacement.CursorPoint(offset = androidx.compose.ui.unit.DpOffset(12.dp, 18.dp)),
    content = content
  )
}

@Composable
private fun SmallButton(icon: Painter, onClick: () -> Unit, tint: Color = TextMuted, tooltip: String? = null) {
  Tip(tooltip) {
    Button(
      onClick = onClick,
      modifier = Modifier.width(24.dp).height(24.dp),
      contentPadding = PaddingValues(2.dp),
      colors = ButtonDefaults.buttonColors(backgroundColor = FieldBg, contentColor = TextMain)
    ) {
      Icon(
        painter = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.width(14.dp).height(14.dp)
      )
    }
  }
}

@Composable
private fun SearchToggleButton(label: String, active: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .height(20.dp)
      .background(if (active) Accent else Color.Transparent, RoundedCornerShape(3.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 5.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(label, color = if (active) Color.White else TextMuted, fontSize = 11.sp)
  }
}

@Composable
private fun IconToolButton(icon: Painter, onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false, tooltip: String? = null) {
  val bg = if (active) Accent else FieldBg
  Tip(tooltip) {
    Button(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.width(28.dp).height(28.dp),
      contentPadding = PaddingValues(4.dp),
      colors = ButtonDefaults.buttonColors(backgroundColor = bg, contentColor = TextMain, disabledBackgroundColor = FieldBg, disabledContentColor = TextMuted)
    ) {
      Icon(
        painter = icon,
        contentDescription = null,
        tint = if (enabled) TextMain else TextMuted,
        modifier = Modifier.width(18.dp).height(18.dp)
      )
    }
  }
}

private fun previewEntry(level: String) = com.android.tools.idea.logcat.standalone.core.LogEntry(0L, level, "T", 1, "m")

@Preview
@Composable
private fun LogcatScreenPreview() {
  val ui = remember {
    ComposeUiBridge(
      deviceProvider = SimpleDeviceProvider(),
      deviceCatalog = DemoDeviceCatalog()
    ).apply {
      devices = listOf(
        DeviceRef("emulator-5554", "Pixel_7_API_34"),
        DeviceRef("R58N12", "Samsung S23")
      )
      selectedDevice = devices.first()
      processOptions = listOf(
        ProcessOption.all(ComposeUiBridge.ALL_PROCESSES_LABEL),
        ProcessOption(false, "com.example.app | app (12345)", "com.example.app", "app", 12345),
        ProcessOption(false, "com.android.systemui | systemui (2222)", "com.android.systemui", "systemui", 2222)
      )
      selectedProcess = processOptions[1]
      query = "package:com.example.app level:info"
      regex = false
      caseSensitive = false
      showTime = true
      showTag = true
      showProcess = true
      visibleRows = listOf(
        RenderedLine(AnnotatedString("12:01:22.101  MainActivity      app                      App started"), "App started", Color(0xFFABC023), "INFO", Color(0xFF6A8759), Color(0xFFE9F5E6), previewEntry("INFO")),
        RenderedLine(AnnotatedString("12:01:22.402  OkHttp            app                      GET /api/user 200"), "GET /api/user 200", Color(0xFF299999), "DEBUG", Color(0xFF305D78), Color(0xFFBBBBBB), previewEntry("DEBUG")),
        RenderedLine(AnnotatedString("12:01:23.017  AuthRepository    app                      Token refresh scheduled"), "Token refresh scheduled", Color(0xFFBBB529), "WARN", Color(0xFFBBB529), Color.Black, previewEntry("WARN")),
        RenderedLine(AnnotatedString("12:01:24.221  CrashlyticsCore   app                      Fatal signal captured"), "Fatal signal captured", Color(0xFFFF6B68), "ERROR", Color(0xFFCF5B56), Color.Black, previewEntry("ERROR"))
      )
    }
  }
  LogcatComposeTheme {
    LogcatComposeScreen(ui)
  }
}

// Чип "какое приложение сейчас раскрыто из package:mine". Клик закрепляет пакет
// (перестаём следовать за экраном) или снова включает слежение.
@Composable
private fun MinePackageChip(pkg: String, pinned: Boolean, onClick: () -> Unit) {
  Tip(if (pinned) "Pinned to this app. Click to follow the app on screen again" else "Following the app on screen. Click to pin this app") {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(3.dp))
      .background(if (pinned) Accent.copy(alpha = 0.35f) else Accent.copy(alpha = 0.15f))
      .border(1.dp, if (pinned) Accent else Color.Transparent, RoundedCornerShape(3.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = (if (pinned) "pinned: " else "mine: ") + pkg,
      color = if (pinned) TextMain else TextMuted,
      fontSize = 11.sp,
      maxLines = 1
    )
  }
  }
}
