package com.cactus.logcactus.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.plugin.LogcatSession
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Вертикальная панель слева, как у штатного Logcat, плюс наши кнопки (закладки, краши, баг-репорт, вид). */
@Composable
fun ToolStrip(
  session: LogcatSession,
  followTail: Boolean,
  onFollowTailChange: (Boolean) -> Unit,
  onBugReportSelection: () -> Unit,
  onBugReportBookmarks: () -> Unit,
  onJump: (LogEntry) -> Unit,
  onPrevBookmark: () -> Unit,
  onNextBookmark: () -> Unit,
  onPrevStack: () -> Unit,
  onNextStack: () -> Unit,
  tools: com.cactus.logcactus.plugin.tools.DeviceTools,
  options: com.cactus.logcactus.plugin.ViewOptions
) {
  var bookmarksMenu by remember { mutableStateOf(false) }
  var crashesMenu by remember { mutableStateOf(false) }
  var bugMenu by remember { mutableStateOf(false) }
  var configMenu by remember { mutableStateOf(false) }
  var deviceMenu by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxHeight().padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    IconActionButton(AllIconsKeys.Actions.GC, "Clear Logcat", onClick = { session.clear() }, tooltip = { Text("Clear Logcat") })
    // Как «Soft-Wrap»: кнопка нажата, пока список следует за новыми строками; прокрутка вверх отжимает её, нажатие — возвращает.
    ToggleableIconActionButton(
      AllIconsKeys.RunConfigurations.Scroll_down, "Scroll to the End", value = followTail, onValueChange = onFollowTailChange,
      tooltip = { Text("Scroll to the End") }
    )
    ToggleableIconActionButton(
      AllIconsKeys.Actions.Pause, "Pause", value = session.paused, onValueChange = { session.togglePause() },
      tooltip = { Text(if (session.paused) "Resume" else "Pause") }
    )
    IconActionButton(AllIconsKeys.Actions.Restart, "Restart", onClick = { session.restart() }, tooltip = { Text("Restart Logcat") })
    ToggleableIconActionButton(
      AllIconsKeys.Actions.ToggleSoftWrap, "Soft-Wrap", value = options.softWrap, onValueChange = { options.applySoftWrap(it) },
      tooltip = { Text("Soft-Wrap") }
    )
    ToggleableIconActionButton(
      AllIconsKeys.Actions.Collapseall, "Fold stack traces", value = options.foldStacks, onValueChange = { options.applyFoldStacks(it) },
      tooltip = { Text("Fold stack traces") }
    )

    IconActionButton(AllIconsKeys.Actions.PreviousOccurence, "Previous stack trace", onClick = onPrevStack, tooltip = { Text("Previous stack trace") })
    IconActionButton(AllIconsKeys.Actions.NextOccurence, "Next stack trace", onClick = onNextStack, tooltip = { Text("Next stack trace") })

    Box {
      IconActionButton(AllIconsKeys.Nodes.Bookmark, "Bookmarks", onClick = { bookmarksMenu = true }, tooltip = { Text("Bookmarks (${session.bookmarks.size})") })
      if (bookmarksMenu) {
        PopupMenu(onDismissRequest = { bookmarksMenu = false; true }, horizontalAlignment = Alignment.Start) {
          selectableItem(selected = false, onClick = { bookmarksMenu = false; onNextBookmark() }) { Text("Next bookmark") }
          selectableItem(selected = false, onClick = { bookmarksMenu = false; onPrevBookmark() }) { Text("Previous bookmark") }
          separator()
          selectableItem(selected = false, onClick = { bookmarksMenu = false; session.clearBookmarks() }) { Text("Clear bookmarks (${session.bookmarks.size})") }
        }
      }
    }

    Box {
      IconActionButton(
        AllIconsKeys.General.Error, "Crashes and ANRs", onClick = { crashesMenu = true },
        enabled = session.crashes.isNotEmpty(),
        tooltip = { Text(if (session.crashes.isEmpty()) "No crashes or ANRs" else "Crashes and ANRs (${session.crashes.sumOf { it.count }})") }
      )
      if (crashesMenu) {
        PopupMenu(onDismissRequest = { crashesMenu = false; true }, horizontalAlignment = Alignment.Start) {
          session.crashes.forEach { c ->
            selectableItem(selected = false, onClick = { crashesMenu = false; onJump(c.lastEntry) }) { Text("${c.kind.name} ×${c.count}  ${c.title.take(80)}") }
          }
        }
      }
    }

    Box {
      IconActionButton(AllIconsKeys.Actions.Share, "Bug report", onClick = { bugMenu = true }, tooltip = { Text("Copy bug report") })
      if (bugMenu) {
        PopupMenu(onDismissRequest = { bugMenu = false; true }, horizontalAlignment = Alignment.Start) {
          selectableItem(selected = false, onClick = { bugMenu = false; onBugReportSelection() }) { Text("Selection, or visible lines if nothing is selected") }
          selectableItem(selected = false, onClick = { bugMenu = false; onBugReportBookmarks() }) { Text("Bookmarks (${session.bookmarks.size})") }
        }
      }
    }

    Box {
      IconActionButton(
        AllIconsKeys.Nodes.Services, "Device and app", enabled = session.selected != null,
        onClick = { deviceMenu = true },
        tooltip = { Text("Device and app actions") }
      )
      if (deviceMenu) {
        PopupMenu(onDismissRequest = { deviceMenu = false; true }, horizontalAlignment = Alignment.Start) {
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.connectWifi() }) { Text("Connect via Wi-Fi…") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.disconnectWifi() }) { Text("Disconnect this Wi-Fi device") }
          separator()
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.restartApp() }) { Text("Restart app") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.forceStop() }) { Text("Force stop app") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.clearCache() }) { Text("Clear app cache") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.clearData() }) { Text("Clear app data…") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.openDeepLink() }) { Text("Open deep link…") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.appInfo() }) { Text("App info") }
          separator()
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.screenshot() }) { Text("Screenshot") }
          separator()
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.saveSession() }) { Text("Save session…") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.openSession() }) { Text("Open session…") }
          selectableItem(selected = false, onClick = { deviceMenu = false; tools.compareWithSession() }) { Text("Compare with session…") }
        }
      }
    }

    Box {
      IconActionButton(AllIconsKeys.General.GearPlain, "Configure formatting", onClick = { configMenu = true }, tooltip = { Text("Configure formatting") })
      if (configMenu) ConfigurePopup(options) { configMenu = false }
    }
  }
}
