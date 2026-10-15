package com.cactus.logcactus.plugin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.plugin.LogcatSession
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val GUTTER = 16.dp
private val PAD = 4.dp

private class MenuState(val row: Int, val x: Int, val y: Int)

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LogcatPanel(session: LogcatSession, tools: com.cactus.logcactus.plugin.tools.DeviceTools, options: com.cactus.logcactus.plugin.ViewOptions) {
  LaunchedEffect(session) { session.start() }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val focus = remember { FocusRequester() }
  val density = LocalDensity.current
  val palette = rememberPalette()

  // Строки списка (свёртка стеков, раскрытие JSON) собираются вне EDT.
  var toggled by remember { mutableStateOf<Set<Int>>(emptySet()) }
  var rows by remember { mutableStateOf<List<Row>>(emptyList()) }
  LaunchedEffect(session.visible, session.foldStacks, session.autoExpandJson, session.autoExpandUrls, toggled) {
    rows = withContext(Dispatchers.Default) { buildRows(session.visible, session.foldStacks, session.autoExpandJson, toggled, session.autoExpandUrls) }
  }

  var sel by remember { mutableStateOf<Selection?>(null) }
  var menu by remember { mutableStateOf<MenuState?>(null) }
  val layouts = remember { HashMap<Int, TextLayoutResult>() }

  // Тексты строк как они показаны — нужны и для отрисовки, и для копирования, и для поиска по клику.
  fun hideTagAt(i: Int): Boolean =
    session.hideRepeatedTags && i > 0 && (rows[i] as? Row.Entry)?.entry?.tag.let { it != null && it == (rows[i - 1] as? Row.Entry)?.entry?.tag }
  fun displayAt(i: Int) = rowDisplay(rows[i], session, hideTagAt(i), palette)

  // Прилипание к концу: пока пользователь внизу — список едет за новыми строками, прокрутил вверх — стоит.
  // Состояние меняется ТОЛЬКО по итогам прокрутки (в конце — прилипаем, иначе нет). Раньше оно выводилось из текущей раскладки
  // списка, и при потере фокуса / скрытии окна (переход в редактор кода) раскладка устаревала, а накопившиеся строки
  // ошибочно снимали прилипание.
  var stickToBottom by remember { mutableStateOf(true) }
  var lastAutoScroll by remember { mutableStateOf(0L) }
  LaunchedEffect(listState) {
    snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
      // Свои докрутки к концу не считаем действием пользователя (строки могут добавиться прямо во время докрутки).
      val ours = System.currentTimeMillis() - lastAutoScroll < 300
      if (!scrolling && !ours && listState.layoutInfo.totalItemsCount > 0) stickToBottom = !listState.canScrollForward
    }
  }
  LaunchedEffect(rows.size, session.paused) {
    if (stickToBottom && !session.paused && rows.isNotEmpty()) { lastAutoScroll = System.currentTimeMillis(); listState.scrollToItem(rows.lastIndex) }
  }

  fun toClipboard(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))
  fun selectionText(): String? = sel?.takeIf { !it.isEmpty && it.startRow < rows.size }?.let { selectedText(rows, { i -> displayAt(i).text }, it) }
  fun selectedEntries(): List<LogEntry> {
    val s = sel?.takeIf { !it.isEmpty } ?: return emptyList()
    return rows.subList(s.startRow.coerceAtMost(rows.size), (s.endRow + 1).coerceAtMost(rows.size)).flatMap { copyLines(listOf(it)) }.distinct()
  }
  fun visibleEntries(): List<LogEntry> = copyLines(listState.layoutInfo.visibleItemsInfo.mapNotNull { rows.getOrNull(it.index) })
  fun bugReport(source: String, entries: List<LogEntry>, texts: List<String>? = null) {
    if (entries.isNotEmpty()) scope.launch { toClipboard(session.bugReport(entries, source, texts)) }
  }
  fun bugReportOfSelection() {
    val text = selectionText()
    if (text != null) bugReport("selection", selectedEntries().ifEmpty { listOfNotNull(rows.getOrNull(sel!!.startRow)?.entry) }, text.lines())
    else bugReport("visible lines", visibleEntries())
  }
  fun jumpTo(entry: LogEntry) {
    val i = rows.indexOfFirst { it.entry === entry }
    if (i >= 0) scope.launch { stickToBottom = false; listState.scrollToItem(i) }
  }
  fun jumpToStack(forward: Boolean) {
    val from = listState.firstVisibleItemIndex
    val range = if (forward) (from + 1 until rows.size) else (from - 1 downTo 0)
    val target = range.firstOrNull { rows[it] is Row.Stack } ?: return
    scope.launch { stickToBottom = false; listState.scrollToItem(target) }
  }
  fun bookmarkStep(forward: Boolean) {
    session.neighbourBookmark(rows.map { it.entry }, listState.firstVisibleItemIndex, forward)?.let(::jumpTo)
  }
  fun toggleFold(index: Int) {
    val k = key(rows[index].entry)
    toggled = if (k in toggled) toggled - k else toggled + k
  }

  Row(
    Modifier.fillMaxSize()
      .focusRequester(focus)
      .focusTarget()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          (e.isMetaPressed || e.isCtrlPressed) && e.key == Key.C -> { selectionText()?.let(::toClipboard); true }
          (e.isMetaPressed || e.isCtrlPressed) && e.key == Key.A -> {
            if (rows.isNotEmpty()) sel = Selection(0, 0, rows.lastIndex, displayAt(rows.lastIndex).length)
            true
          }
          e.key == Key.F2 -> { bookmarkStep(forward = !e.isShiftPressed); true }
          else -> false
        }
      }
  ) {
    ToolStrip(
      session = session,
      followTail = stickToBottom,
      onFollowTailChange = { on ->
        stickToBottom = on
        if (on && rows.isNotEmpty()) scope.launch { lastAutoScroll = System.currentTimeMillis(); listState.scrollToItem(rows.lastIndex) }
      },
      onBugReportSelection = ::bugReportOfSelection,
      onBugReportBookmarks = { bugReport("bookmarked", session.bookmarks.sortedBy { it.timestamp }) },
      onJump = ::jumpTo,
      onPrevBookmark = { bookmarkStep(false) },
      onNextBookmark = { bookmarkStep(true) },
      onPrevStack = { jumpToStack(false) },
      onNextStack = { jumpToStack(true) },
      tools = tools,
      options = options
    )
    Divider(Orientation.Vertical, Modifier.fillMaxHeight())
    Column(Modifier.weight(1f).fillMaxHeight()) {
      Header(session)
      Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
      Box(Modifier.weight(1f).fillMaxWidth()) {
        val hScroll = rememberScrollState()
        val measurer = rememberTextMeasurer()
        val mono = JewelTheme.editorTextStyle
        // Без переноса строк ширина списка — по самой длинной строке (моноширинный шрифт), и он прокручивается по горизонтали.
        val charW = remember(mono) { measurer.measure("M", mono).size.width.toFloat().coerceAtLeast(1f) }
        val longest = remember(rows) { rows.maxOfOrNull { (it as? Row.Entry)?.entry?.message?.length ?: (it as? Row.Detail)?.text?.length ?: 0 } ?: 0 }
        val gutterPx = with(density) { (GUTTER + PAD).toPx() }

        BoxWithConstraints(Modifier.fillMaxSize()) {
          val contentWidth = if (session.softWrap) maxWidth else maxOf(maxWidth, with(density) { (charW * (longest + 70)).toDp() })
          VerticallyScrollableContainer(scrollState = listState) {
            HorizontallyScrollableContainer(scrollState = hScroll) {
              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().width(contentWidth).pointerInput(Unit) {
                  // Выделение мышью по символам через несколько строк; ПКМ — меню; клик по плашке уровня — закладка.
                  fun hit(p: Offset): Triple<Int, Int, Boolean>? {
                    val items = listState.layoutInfo.visibleItemsInfo
                    if (items.isEmpty()) return null
                    val item = items.firstOrNull { p.y >= it.offset && p.y < it.offset + it.size }
                      ?: if (p.y < items.first().offset) items.first() else items.last()
                    val layout = layouts[item.index] ?: return Triple(item.index, 0, false)
                    val local = Offset((p.x - gutterPx).coerceAtLeast(0f), (p.y - item.offset).coerceIn(0f, layout.size.height.toFloat() - 1f))
                    return Triple(item.index, layout.getOffsetForPosition(local), p.x < gutterPx)
                  }
                  var lastClick = 0L
                  var lastRow = -1
                  var clicks = 0
                  awaitPointerEventScope {
                    while (true) {
                      val ev = awaitPointerEvent()
                      if (ev.type != PointerEventType.Press) continue
                      val pos = ev.changes.first().position
                      val h = hit(pos) ?: continue
                      val (index, off, inGutter) = h
                      if (index >= rows.size) continue
                      focus.requestFocus()
                      if (ev.button == PointerButton.Secondary) {
                        val inside = sel?.let { !it.isEmpty && index in it.startRow..it.endRow } == true
                        if (!inside) sel = Selection(index, off, index, off)
                        menu = MenuState(index, pos.x.roundToInt(), pos.y.roundToInt())
                        continue
                      }
                      if (ev.button != PointerButton.Primary) continue
                      val row = rows[index]
                      if (inGutter) {
                        if (row is Row.Stack || (row is Row.Entry && row.expandableJson)) toggleFold(index)
                        else if (row !is Row.Detail) session.toggleBookmark(row.entry)
                        continue
                      }
                      // Плашка уровня — как «кликнуть по бейджу» в основном приложении: ставит/снимает закладку.
                      val display = displayAt(index)
                      val badge = display.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold && it.item.background.alpha > 0f }
                      if (row is Row.Entry && badge != null && off in badge.start until badge.end) { session.toggleBookmark(row.entry); continue }

                      val now = System.currentTimeMillis()
                      clicks = if (index == lastRow && now - lastClick < 350) clicks + 1 else 1
                      lastClick = now; lastRow = index
                      val text = display.text
                      menu = null
                      when {
                        ev.keyboardModifiers.pointerShift && sel != null -> sel = Selection(sel!!.aRow, sel!!.aOff, index, off)
                        clicks == 2 -> {
                          var a = off.coerceIn(0, text.length); var b = a
                          while (a > 0 && isWordChar(text[a - 1])) a--
                          while (b < text.length && isWordChar(text[b])) b++
                          sel = Selection(index, a, index, b)
                        }
                        clicks >= 3 -> sel = Selection(index, 0, index, text.length)
                        else -> sel = Selection(index, off, index, off)
                      }
                      if (clicks >= 2) continue
                      // Тянем: конец выделения следует за указателем, у краёв список докручивается.
                      val anchorRow = sel!!.aRow; val anchorOff = sel!!.aOff
                      val dragStart = if (ev.keyboardModifiers.pointerShift) null else Unit
                      while (true) {
                        val e2 = awaitPointerEvent()
                        val ch = e2.changes.first()
                        if (e2.type == PointerEventType.Release || !ch.pressed) break
                        if (e2.type == PointerEventType.Move && dragStart != null) {
                          val h2 = hit(ch.position) ?: continue
                          sel = Selection(anchorRow, anchorOff, h2.first, h2.second)
                          val viewport = listState.layoutInfo.viewportSize.height
                          if (ch.position.y < 0f) scope.launch { listState.scrollBy(-40f) }
                          else if (ch.position.y > viewport) scope.launch { listState.scrollBy(40f) }
                          ch.consume()
                        }
                      }
                    }
                  }
                }
              ) {
                itemsIndexed(rows) { index, row ->
                  val display = displayAt(index)
                  val range = sel?.rangeIn(index, display.length)
                  val shown = if (range == null || range.isEmpty() && sel?.startRow == sel?.endRow) display
                  else androidx.compose.ui.text.AnnotatedString.Builder(display).apply { addStyle(SpanStyle(background = palette.accent.copy(alpha = 0.35f)), range.first, range.last + 1) }.toAnnotatedString()
                  val bookmarked = row.entry in session.bookmarks && row !is Row.Detail
                  Row(
                    Modifier.fillMaxWidth()
                      .background(if (bookmarked) palette.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                      .padding(horizontal = PAD),
                    verticalAlignment = Alignment.Top
                  ) {
                    Box(Modifier.width(GUTTER).padding(top = 3.dp), contentAlignment = Alignment.TopCenter) {
                      val foldable = row is Row.Stack || (row is Row.Entry && row.expandableJson)
                      when {
                        foldable -> {
                          val open = (row as? Row.Stack)?.expanded ?: (row as Row.Entry).jsonExpanded
                          Icon(if (open) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight, contentDescription = if (open) "Collapse" else "Expand")
                        }
                        bookmarked -> Icon(AllIconsKeys.Nodes.Bookmark, contentDescription = "Bookmark")
                      }
                    }
                    Text(
                      shown, style = mono, modifier = Modifier.weight(1f),
                      softWrap = session.softWrap, maxLines = if (session.softWrap) Int.MAX_VALUE else 1,
                      onTextLayout = { layouts[index] = it }
                    )
                  }
                }
              }
            }
          }
          // Контекстное меню строки в точке нажатия.
          menu?.let { m ->
            Box(Modifier.offset { IntOffset(m.x, m.y) }) {
              PopupMenu(onDismissRequest = { menu = null; true }, horizontalAlignment = Alignment.Start) {
                val row = rows.getOrNull(m.row)
                val entry = row?.entry
                selectableItem(selected = false, onClick = {
                  menu = null
                  (selectionText() ?: entry?.let { LogcatSession.format(it) })?.let(::toClipboard)
                }) { Text("Copy") }
                selectableItem(selected = false, onClick = { menu = null; entry?.let { toClipboard(it.message) } }) { Text("Copy message text") }
                selectableItem(selected = false, onClick = {
                  menu = null
                  val from = (m.row - 25).coerceAtLeast(0)
                  bugReport("around selected line", copyLines(rows.subList(from, (m.row + 26).coerceAtMost(rows.size))).ifEmpty { listOfNotNull(entry) })
                }) { Text("Copy bug report around this line") }
                selectableItem(selected = false, onClick = { menu = null; bugReportOfSelection() }) { Text("Copy bug report (selection or visible lines)") }
                if (entry != null && row !is Row.Detail) {
                  selectableItem(selected = false, onClick = { menu = null; session.toggleBookmark(entry) }) {
                    Text(if (entry in session.bookmarks) "Remove bookmark" else "Add bookmark")
                  }
                }
                if (row is Row.Stack || (row is Row.Entry && row.expandableJson)) {
                  selectableItem(selected = false, onClick = { menu = null; toggleFold(m.row) }) {
                    Text(if ((row as? Row.Stack)?.expanded ?: (row as? Row.Entry)?.jsonExpanded == true) "Collapse" else "Expand")
                  }
                }
                separator()
                selectableItem(selected = false, onClick = {
                  menu = null
                  val q = selectionText() ?: entry?.message ?: ""
                  com.intellij.ide.BrowserUtil.browse("https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
                }) { Text("Search with Google") }
                selectableItem(selected = false, onClick = { menu = null; session.clear() }) { Text("Clear Logcat") }
              }
            }
          }
        }
      }
      session.status?.let { Text(it, color = JewelTheme.globalColors.text.error, modifier = Modifier.padding(4.dp)) }
    }
  }
}

private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' || c == '/' || c == ':'
