package com.cactus.logcactus.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.cactus.logcactus.plugin.LogcatSession
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Верхняя строка как у штатного Logcat: устройство, уровень, поле фильтра с историей и избранным. */
@Composable
fun Header(session: LogcatSession) {
  val filter = rememberTextFieldState(session.query)
  LaunchedEffect(filter) { snapshotFlow { filter.text.toString() }.collect { session.updateQuery(it) } }
  var historyOpen by remember { mutableStateOf(false) }
  var forceAll by remember { mutableStateOf(false) }
  var dismissed by remember { mutableStateOf(false) }
  var picked by remember { mutableStateOf(0) }

  // Автодополнение: контекст по позиции курсора; подсказки пересчитываются при любом изменении текста/курсора/известных значений.
  val text = filter.text.toString()
  val cursor = filter.selection.end
  val ctx = remember(text, cursor) { completionContextAt(text, cursor) }
  val suggestions = remember(ctx, session.known, forceAll) { suggestionsFor(ctx, session.known, forceAll) }
  LaunchedEffect(ctx) { dismissed = false; if (ctx.prefix.isNotEmpty() || ctx.kind != CompletionKind.KEY) forceAll = false }
  LaunchedEffect(suggestions) { picked = 0 }
  val showSuggestions = !dismissed && suggestions.isNotEmpty()
  fun apply(sg: FilterSuggestion) {
    filter.edit {
      replace(ctx.replaceStart, ctx.replaceEnd, sg.insert)
      selection = androidx.compose.ui.text.TextRange(ctx.replaceStart + sg.insert.length)
    }
    forceAll = false
  }

  Row(
    Modifier.fillMaxWidth().padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Dropdown(
      menuContent = {
        if (session.devices.isEmpty()) passiveItem { Text("No devices") }
        session.devices.forEach { d ->
          selectableItem(selected = d.serial == session.selected?.serial, onClick = { session.select(d) }) {
            Text(if (d.online) d.title else "${d.title} — ${d.state}")
          }
        }
      }
    ) { Text(session.selected?.title ?: "No device") }

    if (session.mineActive && session.minePackage.isNotBlank()) {
      val pinned = session.minePinned
      Row(
        Modifier.clip(RoundedCornerShape(3.dp))
          .background(JewelTheme.globalColors.outlines.focused.copy(alpha = if (pinned) 0.35f else 0.15f))
          .clickable { session.toggleMinePin() }
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text((if (pinned) "pinned: " else "mine: ") + session.minePackage, maxLines = 1)
      }
    }

    Box(Modifier.weight(1f)) {
      TextField(
        state = filter,
        outputTransformation = filterOutputTransformation(),
        modifier = Modifier.fillMaxWidth()
          .onFocusChanged { if (!it.isFocused) session.commitQueryToHistory() }
          .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
              // Ctrl+Space — показать все ключи на пустом месте.
              e.key == Key.Spacebar && e.isCtrlPressed -> { forceAll = true; dismissed = false; true }
              showSuggestions && e.key == Key.DirectionDown -> { picked = (picked + 1) % suggestions.size; true }
              showSuggestions && e.key == Key.DirectionUp -> { picked = (picked - 1 + suggestions.size) % suggestions.size; true }
              showSuggestions && (e.key == Key.Enter || e.key == Key.Tab) -> { apply(suggestions[picked.coerceIn(0, suggestions.lastIndex)]); true }
              showSuggestions && e.key == Key.Escape -> { dismissed = true; true }
              e.key == Key.Enter -> { session.commitQueryToHistory(); true }
              else -> false
            }
          },
        placeholder = { Text("Filter: tag:foo level:W package:mine -text …") },
        leadingIcon = {
          Icon(AllIconsKeys.General.Filter, contentDescription = "Filter history", modifier = Modifier.clickable(enabled = session.history.isNotEmpty() || session.favorites.isNotEmpty()) { historyOpen = true })
        },
        trailingIcon = {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (filter.text.isNotEmpty()) {
              Icon(AllIconsKeys.General.Close, contentDescription = "Clear filter", modifier = Modifier.clickable { filter.setTextAndPlaceCursorAtEnd("") })
            }
            // «Cc» (Match case) — внутри поля, между очисткой и звездой, как в Logcat Android Studio.
            val matchCase = session.matchCase
            Tooltip(tooltip = { Text("Match case") }) {
              Box(
                Modifier.clip(RoundedCornerShape(3.dp))
                  .background(if (matchCase) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.35f) else Color.Transparent)
                  .clickable { session.toggleMatchCase() }
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) { Text("Cc") }
            }
            val fav = session.query.trim() in session.favorites
            Icon(
              if (fav) AllIconsKeys.Nodes.Favorite else AllIconsKeys.Nodes.NotFavoriteOnHover,
              contentDescription = "Add / remove from favorites",
              modifier = Modifier.clickable { if (session.query.isNotBlank()) session.toggleFavorite(session.query.trim()) }
            )
          }
        }
      )
      if (showSuggestions) {
        PopupMenu(
          onDismissRequest = { dismissed = true; true },
          horizontalAlignment = Alignment.Start,
          popupProperties = PopupProperties(focusable = false)
        ) {
          suggestions.forEachIndexed { i, sg ->
            selectableItem(selected = i == picked, onClick = { apply(sg) }) {
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(sg.insert.trimEnd())
                if (sg.hint.isNotEmpty()) Text(sg.hint, color = JewelTheme.globalColors.text.info)
              }
            }
          }
        }
      }
      if (historyOpen) {
        PopupMenu(onDismissRequest = { historyOpen = false; true }, horizontalAlignment = Alignment.Start) {
          val favs = session.history.filter { it in session.favorites } + session.favorites.filter { it !in session.history }
          val rest = session.history.filter { it !in session.favorites }
          favs.forEach { q -> selectableItem(selected = false, onClick = { historyOpen = false; filter.setTextAndPlaceCursorAtEnd(q) }) { Text("★ $q") } }
          if (favs.isNotEmpty() && rest.isNotEmpty()) separator()
          rest.forEach { q -> selectableItem(selected = false, onClick = { historyOpen = false; filter.setTextAndPlaceCursorAtEnd(q) }) { Text(q) } }
        }
      }
    }
  }
}

