package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Divider
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.presentation.logcat.CanvasItem
import com.cactus.logcactus.presentation.logcat.RowDetails
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import kotlin.math.ceil

/**
 * Список логов на одном Canvas (режим по умолчанию с 1.3.0): без Compose-узлов на строку. Хранятся только LogEntry
 * (строки-обёртки — CanvasItem); AnnotatedString строится, а текст раскладывается только для видимых строк
 * (раскладка кэшируется в TextMeasurer).
 *
 * Вид держится за ЯКОРЬ — верхнюю полностью видимую строку и её положение на экране: при приходе новых строк,
 * раскрытии/сворачивании посреди списка и выпадении головы буфера строка остаётся на месте (см. [CanvasViewState.restoreAnchor]).
 * Возможности: выделение и копирование (перетаскивание с автопрокруткой, двойной/тройной клик, Cmd/Ctrl+C/A, Esc),
 * контекстное меню, закладки (клик по бейджу, F2), ссылки, свёртка стектрейсов, раскрытие JSON/Intent, подсветка поиска,
 * hideDuplicateTags, скроллбар с перетаскиванием. Доступность минимальная: список целиком одним элементом.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun LogCanvasList(
  ui: ComposeUiBridge,
  hOffset: () -> Int,
  onContentWidth: (Int) -> Unit,
  onViewportWidth: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val measurer = rememberTextMeasurer(cacheSize = 192)
  val density = LocalDensity.current
  val fontSize = ui.logFontSize
  val state = remember { CanvasViewState() }
  var tick by remember { mutableStateOf(0) }
  state.invalidate = { tick++ }
  val clipboard = LocalClipboardManager.current
  val focusRequester = remember { FocusRequester() }
  var menu by remember { mutableStateOf<MenuRequest?>(null) }
  var overLink by remember { mutableStateOf(false) }


  val style = remember(fontSize) {
    TextStyle(
      fontFamily = FontFamily.Monospace,
      fontSize = fontSize.sp,
      lineHeight = (fontSize * 1.35f).sp,
      lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )
  }
  val badgeStyle = remember { TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 11.sp) }
  val lineHeightPx = with(density) { (fontSize * 1.35f).sp.toPx() }
  val badgeW = with(density) { 18.dp.toPx() }
  val badgeH = with(density) { 16.dp.toPx() }
  val barW = with(density) { 3.dp.toPx() }
  val gutterPx = with(density) { 27.dp.toPx() } // полоска закладки 3dp + бейдж 18dp + зазор 6dp
  val sample = remember(style) { measurer.measure("W", style) }
  val charW = sample.size.width.toFloat().coerceAtLeast(1f)
  // Дробная ширина знака (по 40 знакам): целочисленная charW накопила бы ошибку на длинном отступе JSON.
  val exactCharW = remember(style) { measurer.measure("W".repeat(40), style).size.width / 40f }
  // Высота строки для оценок — РЕАЛЬНАЯ измеренная (целые пиксели), а не 1.35*fontSize: иначе на сотнях строк
  // оценка расходится с фактическими высотами и переходы (F2, к крашу) промахиваются на десяток строк.
  val estLineH = sample.size.height.toFloat().coerceAtLeast(1f)
  // Параметры оценок и жестов живут в состоянии и обновляются на каждой композиции: обработчики указателя и адаптер
  // скроллбара создаются один раз и иначе держали бы значения первой композиции (после смены шрифта — устаревшие).
  state.charW = charW
  ui.canvasCharSp = with(density) { exactCharW.toSp().value }
  state.estLineH = estLineH
  state.gutter = gutterPx

  // Запрос прокрутки к записи (закладки F2, переход к крашу): ставим строку у верхнего края.
  val scrollRequest = ui.canvasScrollRequest
  LaunchedEffect(scrollRequest) {
    if (scrollRequest != null) {
      state.scrollToRequest(scrollRequest, ui.canvasItems) { e -> state.estimate(e, ui) }
      ui.follow = false
      ui.canvasScrollRequest = null
      tick++
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
  Canvas(
    modifier = Modifier
      .fillMaxSize()
      .clipToBounds()
      .onSizeChanged {
        state.viewportW = it.width
        state.viewportH = it.height
        onViewportWidth((it.width - gutterPx).toInt())
        tick++
      }
      .semantics {
        // Минимальная доступность: список целиком как один элемент с описанием (построчного дерева семантики у Canvas нет).
        contentDescription = "Log list, ${ui.canvasItems.size} rows"
        stateDescription = if (ui.follow) "following new logs" else "not following new logs"
      }
      .pointerHoverIcon(if (overLink) PointerIcon.Hand else PointerIcon.Text)
      .focusRequester(focusRequester)
      .focusable()
      .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val command = event.isMetaPressed || event.isCtrlPressed
        when {
          command && event.key == Key.C -> {
            val text = state.selectedText(ui.canvasItems, ui)
            if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
            text.isNotEmpty()
          }
          command && event.key == Key.A -> { state.selectAll(ui.canvasItems, ui); true }
          event.key == Key.Escape && state.hasSelection() -> { state.clearSelection(); true }
          else -> false
        }
      }
      .pointerInput(Unit) {
        coroutineScope {
          awaitPointerEventScope {
            while (true) {
              // Ждём именно событие Press (а не awaitFirstDown): для правой кнопки платформа не всегда выставляет "нажато".
              val event = awaitPointerEvent()
              when (event.type) {
                // курсор-рука над ссылкой
                androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.input.pointer.PointerEventType.Enter -> {
                  val p = event.changes.first().position
                  val over = state.linkAt(p.x, p.y, state.gutter, if (wrapNow(ui)) 0f else hOffset().toFloat()) != null
                  if (over != overLink) overLink = over
                  continue
                }
                androidx.compose.ui.input.pointer.PointerEventType.Exit -> { if (overLink) overLink = false; continue }
                else -> Unit
              }
              if (event.type != androidx.compose.ui.input.pointer.PointerEventType.Press) continue
              val down = event.changes.first()
              focusRequester.requestFocus()
              if (event.buttons.isSecondaryPressed || event.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                // правая кнопка: контекстное меню строки (выделение не трогаем)
                state.rowAt(down.position.y)?.let { menu = MenuRequest(it.item, down.position.x, down.position.y) }
                continue
              }
              if (!event.buttons.isPrimaryPressed && event.button != androidx.compose.ui.input.pointer.PointerButton.Primary) continue
              if (down.position.x < state.gutter) {
                // клик по полосе бейджа: закладка на строке
                state.rowAt(down.position.y)?.let { r ->
                  state.bookmarkTextOf(r.item, ui)?.let { text -> ui.toggleBookmarkEntry(r.item.entry, text) }
                }
                continue
              }
              val clicks = (event.nativeEvent as? java.awt.event.MouseEvent)?.clickCount ?: 1
              val shift = if (wrapNow(ui)) 0f else hOffset().toFloat()
              state.pointerX = down.position.x
              state.pointerY = down.position.y
              state.press(down.position.x, down.position.y, clicks, state.gutter, shift, ui)
              val linkUrl = if (clicks == 1) state.linkAt(down.position.x, down.position.y, state.gutter, shift) else null
              var moved = false
              val pressedItem = if (clicks == 1) state.rowAt(down.position.y)?.item else null
              if (clicks > 1) continue
              // перетаскивание: выделение тянется за указателем, у краёв списка — автопрокрутка
              var auto: Job? = null
              drag(down.id) { change ->
                change.consume()
                if (kotlin.math.abs(change.position.x - down.position.x) > 4f || kotlin.math.abs(change.position.y - down.position.y) > 4f) moved = true
                state.pointerX = change.position.x
                state.pointerY = change.position.y
                state.dragTo(state.gutter, if (wrapNow(ui)) 0f else hOffset().toFloat(), ui)
                if (auto == null) {
                  if (ui.follow) ui.follow = false // выделяем — вид замораживаем, новые строки не сдвигают его
                  auto = this@coroutineScope.launch {
                    while (isActive) {
                      delay(AUTOSCROLL_TICK_MS)
                      val outside = state.pointerY < 0f || state.pointerY > state.viewportH
                      if (outside) {
                        val step = (if (state.pointerY < 0f) -state.pointerY else state.pointerY - state.viewportH).coerceIn(4f, 60f)
                        state.autoScroll(if (state.pointerY < 0f) step else -step, ui.canvasItems) { e -> state.estimate(e, ui) }
                        state.dragTo(state.gutter, if (wrapNow(ui)) 0f else hOffset().toFloat(), ui)
                        if (state.offset == 0f && !ui.follow) ui.follow = true // докрутили до конца — снова следим за хвостом
                      }
                    }
                  }
                }
              }
              auto?.cancel()
              if (!moved && linkUrl != null) ui.linkOpener(linkUrl) // клик без перетаскивания по ссылке
              else if (!moved && pressedItem?.kind == CanvasItem.Kind.STACK) ui.toggleCanvasExpanded(pressedItem) // клик по заглушке стека
            }
          }
        }
      }
      .scrollable(
        orientation = Orientation.Vertical,
        state = rememberScrollableState { delta ->
          // delta > 0 — содержимое едет вниз (смотрим более ранние строки): offset растёт.
          val rows = ui.canvasItems
          val before = state.offset
          val max = state.totalHeight(rows) { e -> state.estimate(e, ui) } - state.viewportH
          state.applyOffset((state.offset + delta).coerceIn(0f, max.coerceAtLeast(0f)))
          if (state.offset > 0f && ui.follow) ui.follow = false
          if (state.offset == 0f && delta < 0f && !ui.follow) ui.follow = true
          tick++
          state.offset - before
        }
      )
  ) {
    tick // подписка на перерисовку от ввода
    val rows = ui.canvasItems
    val wrap = ui.wrapLines
    val textWidth = (size.width - gutterPx).coerceAtLeast(1f)

    // Кэши форматирования/высот сбрасываются при смене настроек вида (те же, что инвалидируют RenderCache).
    val signature = ui.canvasSignature() + "|" + wrap + "|" + fontSize + "|" + textWidth.toInt()
    if (signature != state.signature) {
      state.signature = signature
      state.formatted.clear()
      state.heights.clear()
      state.clearSelection(redraw = false) // смещения в тексте строк уже не соответствуют новому форматированию
      state.maxContentW = 0 // ширина считается заново при новом шрифте/колонках
    }
    if (state.formatted.size > MAX_CACHE) state.formatted.clear()
    if (state.heights.size > MAX_CACHE * 4) state.heights.clear()

    if (ui.follow) state.offset = 0f
    else if (rows !== state.lastRows) state.restoreAnchor(rows) { e -> state.estimate(e, ui) }
    state.syncRows(rows)

    val constraints = if (wrap) Constraints(maxWidth = textWidth.toInt().coerceAtLeast(1)) else Constraints()
    // Карточка JSON не доходит до края окна (скроллбар), поэтому перенос строк внутри неё — раньше.
    val jsonConstraints = if (wrap) Constraints(maxWidth = (textWidth - JSON_CARD_RIGHT_MARGIN.dp.toPx()).toInt().coerceAtLeast(1)) else constraints
    state.rects.clear()
    val highlight = ui.canvasSearchHighlight
    var topIndex = -1
    var anchorItem: CanvasItem? = null
    var anchorTopY = 0f
    val selection = state.selectionSpan(rows)
    var y = size.height + state.offset // нижняя граница последней строки
    var i = rows.lastIndex
    var maxWidth = state.maxContentW
    clipRect {
      while (i >= 0 && y > 0f) {
        val item = rows[i]
        // Строка далеко ниже экрана (прокрутили вверх): ни форматирования, ни раскладки — только высота (кэш или оценка).
        val below = y > size.height + lineHeightPx * BELOW_SCREEN_LINES
        val h: Float
        if (below) {
          h = state.heights[item] ?: state.estimate(item, ui)
        } else {
          val text = state.textAt(rows, i, ui)
          val layout = measurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = wrap,
            maxLines = if (wrap) Int.MAX_VALUE else 1,
            constraints = if (item.json) jsonConstraints else constraints
          )
          h = layout.size.height.toFloat()
          state.heights[item] = h
          if (!wrap && layout.size.width > maxWidth) maxWidth = layout.size.width
          val top = y - h
          if (top < size.height) {
            val shift = if (wrap) 0f else hOffset().toFloat()
            state.rects.add(RowRect(i, item, top, h, layout))
            // верхняя ПОЛНОСТЬЮ видимая строка — индекс для F2/навигации и якорь вида; если целой нет — самая верхняя частичная
            if (top >= -1f || topIndex < 0) { topIndex = i; anchorItem = item; anchorTopY = top }
            val sel = selection?.rangeFor(i, layout.layoutInput.text.length)
            drawRow(item, layout, top, h, ui, measurer, badgeStyle, badgeW, badgeH, barW, gutterPx, lineHeightPx, shift, sel, bookmarkedItem(item, ui), highlight?.byRow?.get(i), highlight?.current,
              jsonRight = if (wrap) 0f else gutterPx - shift + maxWidth)
          }
        }
        y -= h
        i--
      }
    }
    if (topIndex >= 0) ui.canvasTopIndex = topIndex
    ui.canvasSelectionText = { if (state.hasSelection()) state.selectedText(ui.canvasItems, ui) else "" }
    ui.canvasVisibleLines = { state.visibleLines(ui.canvasItems, ui) }
    state.anchorItem = anchorItem
    state.anchorTop = anchorTopY
    state.lastRows = rows
    // Ширина содержимого: при пустом списке начинаем заново, иначе горизонтальный скролл после очистки ждал бы строки шире прежней
    if (rows.isEmpty()) maxWidth = 0
    if (maxWidth != state.maxContentW) {
      state.maxContentW = maxWidth
      onContentWidth(maxWidth)
    }
  }
  VerticalScrollbar(
    adapter = remember(state, ui) {
      CanvasScrollbarAdapter(state, ui, { e -> state.estimate(e, ui) }, { tick }, { tick++ })
    },
    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
  )
  menu?.let { m ->
    val item = m.item
    val entry = item.entry
    val text = { state.bookmarkTextOf(item, ui) ?: state.textOf(item, ui).text }
    val close = { menu = null }
    val bookmarked = bookmarkedItem(item, ui)
    val expandable = item.kind != CanvasItem.Kind.ENTRY || RowDetails.expansion(entry.message) != null
    val expandLabel = when (item.kind) {
      CanvasItem.Kind.STACK -> if (item.expanded) "Collapse Stack Frames" else "Expand Stack Frames"
      CanvasItem.Kind.HEAD, CanvasItem.Kind.DETAIL -> "Collapse JSON / Intent / URL"
      CanvasItem.Kind.ENTRY -> if (expandable) "Expand JSON / Intent / URL" else null
    }
    // Popup привязан к низу контейнера списка, поэтому по Y смещаем от нижнего края.
    DropdownMenu(
      expanded = true,
      onDismissRequest = close,
      offset = with(density) { DpOffset(m.x.toDp(), (m.y - state.viewportH).toDp()) }
    ) {
      MenuColumn(minWidth = 260.dp) {
        MenuEntry(
          "Copy",
          onClick = {
            close()
            val selected = if (state.hasSelection()) state.selectedText(ui.canvasItems, ui) else text()
            clipboard.setText(AnnotatedString(selected))
          },
          hint = "Cmd/Ctrl+C"
        )
        MenuEntry("Copy Message Text", onClick = { close(); clipboard.setText(AnnotatedString(if (item.kind == CanvasItem.Kind.DETAIL) item.line.trim() else entry.message)) })
        MenuEntry("Copy Bug Report", onClick = { close(); ui.copyBugReport { clipboard.setText(AnnotatedString(it)) } })
        if (item.kind != CanvasItem.Kind.DETAIL) MenuEntry(if (bookmarked) "Remove Bookmark" else "Add Bookmark", onClick = { close(); ui.toggleBookmarkEntry(entry, text()) })
        if (expandLabel != null) MenuEntry(expandLabel, onClick = { close(); ui.toggleCanvasExpanded(item) })
        Divider(color = Color(0xFF3B424E), modifier = Modifier.padding(vertical = 4.dp))
        MenuEntry("Search with Google", onClick = { close(); ui.searchGoogle(entry.message) })
        MenuEntry("Clear Logcat", onClick = { close(); ui.clearLogs() })
      }
    }
  }
  }
}

private fun bookmarkedItem(item: CanvasItem, ui: ComposeUiBridge): Boolean =
  if (item.kind == CanvasItem.Kind.STACK) item.hidden.any { ui.isBookmarked(it) } else ui.isBookmarked(item.entry)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRow(
  item: CanvasItem,
  layout: TextLayoutResult,
  top: Float,
  height: Float,
  ui: ComposeUiBridge,
  measurer: androidx.compose.ui.text.TextMeasurer,
  badgeStyle: TextStyle,
  badgeW: Float,
  badgeH: Float,
  barW: Float,
  gutterPx: Float,
  lineHeightPx: Float,
  hShift: Float,
  selection: IntRange? = null,
  bookmarked: Boolean = false,
  matches: List<SearchMatch>? = null,
  currentMatch: SearchMatch? = null,
  jsonRight: Float = 0f
) {
  val entry = item.entry
  val colors = ui.levelColors(entry.level)
  if (bookmarked) {
    // как в списке на Compose-узлах: подсветка строки и полоска слева
    drawRect(BOOKMARK_TINT, Offset(0f, top), Size(size.width, height))
    drawRect(BOOKMARK_BAR, Offset(0f, top), Size(barW, height))
  }
  // бейдж уровня: по центру первой строки (правее полоски закладки); у дочерних строк раскрытия JSON его нет
  if (item.kind != CanvasItem.Kind.DETAIL) {
    val by = top + (lineHeightPx - badgeH) / 2f
    drawRoundRect(colors.badgeBg, Offset(barW, by), Size(badgeW, badgeH), CornerRadius(2.dp.toPx()))
    val letter = measurer.measure(letterOf(entry.level), badgeStyle)
    drawText(letter, colors.badgeFg, Offset(barW + (badgeW - letter.size.width) / 2f, by + (badgeH - letter.size.height) / 2f))
  }
  clipRect(left = gutterPx) {
    if (item.kind == CanvasItem.Kind.DETAIL && item.json) drawJsonCard(item, layout, top, height, colors.message, ui.isDarkTheme(), gutterPx - hShift, jsonRight)
    if (matches != null) {
      // подсветка найденного (Cmd+F): текущее совпадение другим цветом
      val len = layout.layoutInput.text.length
      translate(gutterPx - hShift, top) {
        for (m in matches) {
          val from = m.start.coerceIn(0, len)
          val to = m.end.coerceIn(0, len)
          if (to > from) drawPath(layout.getPathForRange(from, to), if (m == currentMatch) SearchCurrentMatchBg else SearchMatchBg)
        }
      }
    }
    if (selection != null && !selection.isEmpty()) {
      translate(gutterPx - hShift, top) { drawPath(layout.getPathForRange(selection.first, selection.last + 1), SELECTION_COLOR) }
    }
    drawText(layout, colors.message, Offset(gutterPx - hShift, top))
  }
}

/**
 * Строки раскрытого JSON рисуются одной карточкой: нейтральный чуть отличающийся фон и полоска цвета уровня слева, скругления
 * только у первой и последней строки. Карточка начинается чуть левее текста JSON (в пустом отступе под колонки).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawJsonCard(
  item: CanvasItem,
  layout: TextLayoutResult,
  top: Float,
  height: Float,
  levelColor: Color,
  dark: Boolean,
  textX: Float,
  contentRight: Float
) {
  val pad = 10.dp.toPx()
  val r = 5.dp.toPx()
  val prefix = (layout.layoutInput.text.length - item.line.length).coerceAtLeast(0)
  val left = textX + layout.getHorizontalPosition(prefix, true) - pad
  // не заходим под вертикальный скроллбар; без переноса строк карточка тянется до конца самой длинной строки списка
  val right = maxOf(size.width - JSON_CARD_RIGHT_MARGIN.dp.toPx(), contentRight + pad)
  if (right <= left) return
  // Скругление рисуем «за краем» строки и обрезаем по её границам: соседние строки карточки стыкуются без швов.
  val y0 = if (item.first) top else top - r
  val y1 = if (item.last) top + height else top + height + r
  val shape = androidx.compose.ui.graphics.Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(left, y0, right, y1, CornerRadius(r)))
  }
  clipRect(top = top, bottom = top + height) {
    clipPath(shape) {
      drawRect(if (dark) Color(0x0DFFFFFF) else Color(0x0A000000), Offset(left, y0), Size(right - left, y1 - y0))
      drawRect(levelColor.copy(alpha = 0.85f), Offset(left, y0), Size(3.dp.toPx(), y1 - y0))
    }
  }
}

private fun letterOf(level: String): String = when (level) {
  "ERROR" -> "E"
  "ASSERT" -> "A"
  "WARN" -> "W"
  "INFO" -> "I"
  "DEBUG" -> "D"
  else -> "V"
}

private const val JSON_CARD_RIGHT_MARGIN = 18
private const val MAX_CACHE = 1200
private const val BELOW_SCREEN_LINES = 60
private const val MAX_INDENT = 160
private const val AUTOSCROLL_TICK_MS = 30L
private val SELECTION_COLOR = Color(0x734A78B9)
private val BOOKMARK_TINT = Color(0x294A78B9)
private val BOOKMARK_BAR = Color(0xFF4A78B9)

private fun wrapNow(ui: ComposeUiBridge) = ui.wrapLines

// Изменяемое состояние отрисовки — обычные поля (не Compose-state), чтобы менять их прямо во время draw.
private class CanvasViewState {
  var offset = 0f // на сколько пикселей прокручено вверх относительно низа списка (0 — следим за концом)
  var viewportW = 0
  var viewportH = 0
  var maxContentW = 0
  var signature = ""
  var lastRows: List<CanvasItem> = emptyList()
  // Якорь вида: верхняя полностью видимая строка и её положение (y верхнего края) в последнем кадре.
  var anchorItem: CanvasItem? = null
  var anchorTop = 0f
  // Параметры оценок высот/жестов (обновляются на каждой композиции).
  var charW = 8f
  var estLineH = 18f
  var gutter = 0f
  val formatted = IdentityHashMap<CanvasItem, AnnotatedString>()
  val heights = IdentityHashMap<CanvasItem, Float>()

  /** Видимый текст строки (строится в мосте: [ComposeUiBridge.canvasItemText]), кэшируется по объекту строки. */
  fun textOf(item: CanvasItem, ui: ComposeUiBridge, previousTag: String? = null): AnnotatedString =
    formatted.getOrPut(item) { ui.canvasItemText(item, previousTag) }

  /** Текст строки по индексу: для hideDuplicateTags тег гасится, если он совпал с тегом предыдущей строки. */
  fun textAt(rows: List<CanvasItem>, index: Int, ui: ComposeUiBridge): AnnotatedString {
    val item = rows[index]
    val previousTag = if (ui.hideDuplicateTags && index > 0) rows[index - 1].entry.tag else null
    // первая строка окна лишена предшественника: при hideDuplicateTags её текст не кэшируем (он зависит от позиции)
    return if (ui.hideDuplicateTags && index == 0) ui.canvasItemText(item, null) else textOf(item, ui, previousTag)
  }

  /** Что копировать для строки целиком: заглушке соответствуют её кадры, HEAD — исходная запись, DETAIL — ничего. */
  fun copyTextOf(item: CanvasItem, ui: ComposeUiBridge): String? = when (item.kind) {
    CanvasItem.Kind.ENTRY -> textOf(item, ui).text
    CanvasItem.Kind.HEAD -> ui.formatForCanvas(item.entry).text
    CanvasItem.Kind.STACK -> if (item.expanded) null else item.hidden.joinToString("\n") { ui.formatForCanvas(it).text }
    CanvasItem.Kind.DETAIL -> null
  }

  /** Текст закладки на строке: заглушка стека (свёрнутая или раскрытая) — её кадры, HEAD — запись целиком; на DETAIL закладок нет. */
  fun bookmarkTextOf(item: CanvasItem, ui: ComposeUiBridge): String? = when (item.kind) {
    CanvasItem.Kind.ENTRY -> textOf(item, ui).text
    CanvasItem.Kind.HEAD -> ui.formatForCanvas(item.entry).text
    CanvasItem.Kind.STACK -> item.hidden.joinToString("\n") { ui.formatForCanvas(it).text }
    CanvasItem.Kind.DETAIL -> null
  }

  // Оценка высоты строки без раскладки: моноширинный шрифт, считаем символы (перенос по словам даёт ±1 строку).
  fun estimate(item: CanvasItem, ui: ComposeUiBridge): Float {
    val lineHeightPx = estLineH
    val gutterPx = gutter
    if (!ui.wrapLines) return lineHeightPx
    val prefix = (if (ui.showTime) ui.timeWidth + 2 else 0) + (if (ui.showTag) ui.tagWidth + 2 else 0) +
      (if (ui.showAppName) ui.appNameWidth + 2 else 0) + (if (ui.showProcess) ui.processWidth + 2 else 0)
    val length = when (item.kind) {
      CanvasItem.Kind.ENTRY -> item.entry.message.length
      CanvasItem.Kind.HEAD -> item.payloadStart
      CanvasItem.Kind.STACK -> 24
      CanvasItem.Kind.DETAIL -> item.line.length
    }
    val perLine = ((viewportW - gutterPx) / charW).toInt().coerceAtLeast(10)
    return ceil((prefix + length).toFloat() / perLine).coerceAtLeast(1f) * lineHeightPx
  }

  // ---- выделение ----
  var invalidate: () -> Unit = {}
  var pointerX = 0f
  var pointerY = 0f
  val rects = ArrayList<RowRect>() // строки, нарисованные в последнем кадре (снизу вверх)
  private var anchor: SelPoint? = null
  private var focus: SelPoint? = null

  /** Текст всех целиком или частично видимых строк (сверху вниз) — для баг-репорта без выделения. */
  fun visibleLines(rows: List<CanvasItem>, ui: ComposeUiBridge): List<String> =
    rects.sortedBy { it.index }.map { textAt(rows, it.index, ui).text }

  fun hasSelection(): Boolean = anchor != null && focus != null && (anchor!!.item !== focus!!.item || anchor!!.offset != focus!!.offset)

  fun clearSelection(redraw: Boolean = true) {
    anchor = null
    focus = null
    if (redraw) invalidate()
  }

  fun syncRows(rows: List<CanvasItem>) {
    // Выделенные строки пропали (выпали из буфера или перестроились при свёртке) — выделение снимаем.
    val a = anchor ?: return
    val f = focus ?: return
    if (indexOf(rows, a.item) < 0 || indexOf(rows, f.item) < 0) { anchor = null; focus = null }
  }

  private fun indexOf(rows: List<CanvasItem>, item: CanvasItem): Int {
    var i = rows.lastIndex
    while (i >= 0) { if (rows[i] === item) return i; i-- }
    return -1
  }

  /** Индекс строки, отвечающей записи [entry]: сама запись, её HEAD или заглушка стека, скрывающая этот кадр. */
  private fun indexOfEntry(rows: List<CanvasItem>, entry: LogEntry): Int {
    var i = rows.lastIndex
    while (i >= 0) {
      val it = rows[i]
      if (it.kind != CanvasItem.Kind.DETAIL && it.covers(entry)) return i
      i--
    }
    return -1
  }

  /** Строка и смещение в её тексте под точкой (x, y); за пределами нарисованных строк — край ближайшей. */
  private fun hit(x: Float, y: Float, gutterPx: Float, hShift: Float): Pair<RowRect, Int>? {
    if (rects.isEmpty()) return null
    var rect = rects.firstOrNull { y >= it.top && y < it.top + it.height }
    var offset: Int
    if (rect == null) {
      val highest = rects.last()
      val lowest = rects.first()
      if (y < highest.top) { rect = highest; offset = 0 } else { rect = lowest; offset = rect.layout.layoutInput.text.length }
      return rect to offset
    }
    offset = rect.layout.getOffsetForPosition(Offset(x - (gutterPx - hShift), y - rect.top))
    return rect to offset
  }

  fun rowAt(y: Float): RowRect? = rects.firstOrNull { y >= it.top && y < it.top + it.height }

  /** URL ссылки под точкой (x, y) или null: ссылки помечены аннотацией URL_TAG в отформатированном тексте строки. */
  fun linkAt(x: Float, y: Float, gutterPx: Float, hShift: Float): String? {
    val rect = rowAt(y) ?: return null
    val text = rect.layout.layoutInput.text
    val offset = rect.layout.getOffsetForPosition(Offset(x - (gutterPx - hShift), y - rect.top))
    return text.getStringAnnotations(ComposeUiBridge.URL_TAG, offset, offset).firstOrNull()?.item
  }

  /** Запрос прокрутки: к конкретной строке, если она задана и ещё в списке, иначе к записи. */
  fun scrollToRequest(req: ComposeUiBridge.CanvasScroll, rows: List<CanvasItem>, est: (CanvasItem) -> Float) {
    val idx = req.item?.let { indexOf(rows, it) } ?: -1
    if (idx >= 0) scrollToIndex(idx, rows, est) else scrollToEntry(req.entry, rows, est)
  }

  private fun scrollToIndex(idx: Int, rows: List<CanvasItem>, est: (CanvasItem) -> Float) {
    var sum = 0f
    for (k in idx..rows.lastIndex) sum += heights[rows[k]] ?: est(rows[k])
    offset = (sum - viewportH).coerceAtLeast(0f)
    anchorItem = null // якорь перехватит следующий кадр
  }

  /** Ставит запись у верхнего края списка: расстояние от низа до её верха = сумма высот строк idx..последняя. */
  fun scrollToEntry(entry: LogEntry, rows: List<CanvasItem>, est: (CanvasItem) -> Float) {
    val idx = indexOfEntry(rows, entry)
    if (idx < 0) return
    var sum = 0f
    for (k in idx..rows.lastIndex) sum += heights[rows[k]] ?: est(rows[k])
    offset = (sum - viewportH).coerceAtLeast(0f)
    anchorItem = null
  }

  fun press(x: Float, y: Float, clicks: Int, gutterPx: Float, hShift: Float, ui: ComposeUiBridge) {
    val (rect, offset) = hit(x, y, gutterPx, hShift) ?: return
    val len = rect.layout.layoutInput.text.length
    when {
      clicks >= 3 -> { anchor = SelPoint(rect.item, 0); focus = SelPoint(rect.item, len) }
      clicks == 2 -> {
        val word = rect.layout.getWordBoundary(offset.coerceIn(0, (len - 1).coerceAtLeast(0)))
        anchor = SelPoint(rect.item, word.start); focus = SelPoint(rect.item, word.end)
      }
      else -> { anchor = SelPoint(rect.item, offset); focus = anchor }
    }
    invalidate()
  }

  fun dragTo(gutterPx: Float, hShift: Float, ui: ComposeUiBridge) {
    val (rect, offset) = hit(pointerX, pointerY, gutterPx, hShift) ?: return
    focus = SelPoint(rect.item, offset)
    invalidate()
  }

  fun autoScroll(delta: Float, rows: List<CanvasItem>, est: (CanvasItem) -> Float) {
    val max = totalHeight(rows, est) - viewportH
    applyOffset((offset + delta).coerceIn(0f, max.coerceAtLeast(0f)))
    invalidate()
  }

  fun selectAll(rows: List<CanvasItem>, ui: ComposeUiBridge) {
    if (rows.isEmpty()) return
    val last = rows.last()
    anchor = SelPoint(rows.first(), 0)
    focus = SelPoint(last, textAt(rows, rows.lastIndex, ui).length)
    invalidate()
  }

  /** Упорядоченный диапазон выделения в терминах индексов строк; null — выделения нет. */
  fun selectionSpan(rows: List<CanvasItem>): SelectionSpan? {
    val a = anchor ?: return null
    val f = focus ?: return null
    val ai = indexOf(rows, a.item)
    val fi = indexOf(rows, f.item)
    if (ai < 0 || fi < 0) return null
    val forward = ai < fi || (ai == fi && a.offset <= f.offset)
    return if (forward) SelectionSpan(ai, a.offset, fi, f.offset) else SelectionSpan(fi, f.offset, ai, a.offset)
  }

  /**
   * Текст выделения (как из SelectionContainer: видимый текст строк через \n). Обычные строки копируются по символам
   * выделения; заглушка стека даёт свои кадры; HEAD раскрытого JSON — исходную запись целиком (тогда её дочерние строки
   * пропускаются, чтобы не дублировать); если выделение начинается уже внутри дерева JSON — копируются выделенные части
   * самих строк дерева.
   */
  fun selectedText(rows: List<CanvasItem>, ui: ComposeUiBridge): String {
    val span = selectionSpan(rows) ?: return ""
    val parts = ArrayList<String>()
    val copiedWhole = IdentityHashMap<LogEntry, Boolean>()
    for (i in span.startRow..span.endRow) {
      val item = rows[i]
      val text = textAt(rows, i, ui).text
      val range = span.rangeFor(i, text.length) ?: continue
      val isEmpty = range.isEmpty()
      when (item.kind) {
        CanvasItem.Kind.ENTRY -> {
          if (isEmpty) { if (span.startRow != span.endRow) parts.add("") }
          else parts.add(text.substring(range.first, range.last + 1))
        }
        CanvasItem.Kind.HEAD, CanvasItem.Kind.STACK -> {
          if (!isEmpty || span.startRow != span.endRow) copyTextOf(item, ui)?.let {
            parts.add(it)
            if (item.kind == CanvasItem.Kind.HEAD) copiedWhole[item.entry] = true
          }
        }
        CanvasItem.Kind.DETAIL -> {
          if (!isEmpty && copiedWhole[item.entry] == null) parts.add(text.substring(range.first, range.last + 1).trimStart())
        }
      }
    }
    return parts.joinToString("\n")
  }

  private var totalFor: List<CanvasItem>? = null
  private var totalHeightsSize = -1
  private var totalValue = 0f

  /** Общая высота с кэшем: скроллбар спрашивает её по нескольку раз за кадр, пересчёт только при смене списка/измерений. */
  fun totalCached(rows: List<CanvasItem>, est: (CanvasItem) -> Float): Float {
    if (totalFor !== rows || totalHeightsSize != heights.size) {
      totalValue = totalHeight(rows, est)
      totalFor = rows
      totalHeightsSize = heights.size
    }
    return totalValue
  }

  fun totalHeight(rows: List<CanvasItem>, est: (CanvasItem) -> Float): Float {
    var sum = 0f
    for (e in rows) sum += heights[e] ?: est(e)
    return sum
  }

  /** Меняет сдвиг и двигает якорь вместе с содержимым: жест прокрутки не теряется, если список обновился в том же кадре. */
  fun applyOffset(newOffset: Float) {
    anchorTop += newOffset - offset
    offset = newOffset
  }

  /**
   * Список изменился, а слежения за концом нет: возвращаем якорную строку на прежнее место. Сдвиг считаем от низа:
   * верх якоря = низ списка − сумма высот строк от якоря до конца, отсюда offset = anchorTop + сумма − высота области.
   * Так вид не плывёт при новых строках снизу, при раскрытии/сворачивании посреди списка и при выпадении головы буфера.
   * Якорь исчез (выпал из буфера) — сдвиг не трогаем.
   */
  fun restoreAnchor(rows: List<CanvasItem>, est: (CanvasItem) -> Float) {
    val a = anchorItem ?: return
    val idx = indexOfAnchor(rows, a)
    if (idx < 0) return
    var sum = 0f
    for (k in idx..rows.lastIndex) sum += heights[rows[k]] ?: est(rows[k])
    offset = (anchorTop + sum - viewportH).coerceAtLeast(0f)
  }

  /** Строка-якорь в новом списке: тот же объект, а если строку пересобрали (заглушка с другим числом кадров) — та же запись и вид. */
  private fun indexOfAnchor(rows: List<CanvasItem>, anchor: CanvasItem): Int {
    var fallback = -1
    var i = rows.lastIndex
    while (i >= 0) {
      val r = rows[i]
      if (r === anchor) return i
      if (r.entry === anchor.entry && r.kind == anchor.kind && r.line == anchor.line) fallback = i
      i--
    }
    return fallback
  }
}

private class MenuRequest(val item: CanvasItem, val x: Float, val y: Float)

private class SelPoint(val item: CanvasItem, val offset: Int)

private class RowRect(val index: Int, val item: CanvasItem, val top: Float, val height: Float, val layout: TextLayoutResult)

private class SelectionSpan(val startRow: Int, val startOffset: Int, val endRow: Int, val endOffset: Int) {
  /** Выделенные символы строки с индексом [row] (textLength — длина её текста); null — строка вне выделения. */
  fun rangeFor(row: Int, textLength: Int): IntRange? {
    if (row < startRow || row > endRow) return null
    val from = if (row == startRow) startOffset else 0
    val to = if (row == endRow) endOffset else textLength
    val lo = from.coerceIn(0, textLength)
    val hi = to.coerceIn(0, textLength)
    return if (hi > lo) lo until hi else IntRange.EMPTY
  }
}

// Адаптер скроллбара: положение — от верха списка (наш offset считается от низа), размер — оценка общей высоты.
// Состояния Compose (tick, canvasItems) читаются в геттерах, чтобы ползунок обновлялся вместе со списком.
private class CanvasScrollbarAdapter(
  private val state: CanvasViewState,
  private val ui: ComposeUiBridge,
  private val est: (CanvasItem) -> Float,
  private val tick: () -> Int,
  private val onScroll: () -> Unit
) : androidx.compose.foundation.v2.ScrollbarAdapter {
  private fun maxOffset(): Float {
    tick()
    return (state.totalCached(ui.canvasItems, est) - state.viewportH).coerceAtLeast(0f)
  }

  override val scrollOffset: Double get() = (maxOffset() - state.offset).coerceIn(0f, maxOffset()).toDouble()
  override val contentSize: Double get() = maxOffset().toDouble() + state.viewportH
  override val viewportSize: Double get() = state.viewportH.toDouble()

  override suspend fun scrollTo(scrollOffset: Double) {
    state.applyOffset((maxOffset() - scrollOffset.toFloat()).coerceIn(0f, maxOffset()))
    if (state.offset > 0f && ui.follow) ui.follow = false
    if (state.offset == 0f && !ui.follow) ui.follow = true
    onScroll()
  }
}
