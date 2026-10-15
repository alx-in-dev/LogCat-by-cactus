package com.cactus.logcactus.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.android.tools.idea.logcat.standalone.core.DeviceProvider
import com.android.tools.idea.logcat.standalone.core.LogStreamGateway
import com.android.tools.idea.logcat.standalone.core.LogcatSessionController
import com.android.tools.idea.logcat.standalone.core.PreferencesStore
import com.cactus.logcactus.infrastructure.di.logcatAppModule
import com.cactus.logcactus.mcp.McpRuntime
import com.cactus.logcactus.presentation.logcat.compose.ComposeUiBridge
import com.cactus.logcactus.presentation.logcat.compose.LogcatComposeScreen
import com.cactus.logcactus.presentation.logcat.compose.LogcatComposeTheme
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

private val TabBarBg = Color(0xFF1E1F22)
private val TabBg = Color(0xFF23262D)
private val TabActiveBg = Color(0xFF2A2E35)
private val TabBorder = Color(0xFF3B424E)
private val Accent = Color(0xFF4A78B9)
private val TextMain = Color(0xFFE6ECF7)
private val TextMuted = Color(0xFFA4AFBF)

private class LogcatTab(
  val id: String,
  val title: String,
  val bridge: ComposeUiBridge,
  val controller: LogcatSessionController?
)

private fun newLogStreamGateway(): LogStreamGateway {
  val backend = System.getProperty("logcactus.backend", "adb").trim().lowercase()
  return if (backend == "adb") AdbLogStreamGateway() else SimpleLogStreamGateway()
}

fun main() {
  // Красим нативный title bar в тёмный (иначе на macOS он остаётся светлым
  // поверх тёмного Compose-контента). Должно быть выставлено до создания
  // первого AWT/Swing-компонента.
  System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")
  UiFreezeWatchdog.start()
  runLogcatApplication()
}

private fun runLogcatApplication() = application {
  val koin = remember {
    startKoin {
      modules(logcatAppModule)
    }.koin
  }
  val deviceProvider = remember { koin.get<SimpleDeviceProvider>() }
  val deviceCatalog = remember { koin.get<DeviceCatalog>() }
  val preferencesStore = remember { koin.get<PreferencesStore>() }
  val viewSettingsStore = remember { ViewSettingsStore() }

  val tabs = remember { mutableStateListOf<LogcatTab>() }
  var activeTabId by remember { mutableStateOf("") }
  var tabCounter by remember { mutableStateOf(0) }

  // У каждой вкладки свой провайдер выбора устройства: смена устройства в одной вкладке не переключает остальные.
  val providers = HashMap<String, SimpleDeviceProvider>()
  fun providerForNewTab(): SimpleDeviceProvider {
    val template = tabs.firstOrNull { it.id == activeTabId }?.let { providers[it.id] } ?: deviceProvider
    return SimpleDeviceProvider().also { p ->
      p.selectDeviceSilently(template.selectedDevice(), template.selectedClient())
    }
  }

  fun openSessionTab(title: String, session: SessionFile.Session) {
    tabCounter += 1
    val provider = providerForNewTab()
    val bridge = ComposeUiBridge(provider, deviceCatalog, viewSettingsStore)
    bridge.onOpenSession = { t, s -> openSessionTab(t, s) }
    bridge.loadSession(session)
    providers["tab-$tabCounter"] = provider
    val tab = LogcatTab("tab-$tabCounter", "Session: $title", bridge, null)
    tabs.add(tab)
    activeTabId = tab.id
  }

  fun createTab(): LogcatTab {
    tabCounter += 1
    val title = if (tabCounter == 1) "Logcat" else "Logcat ($tabCounter)"
    val provider = if (tabs.isEmpty()) deviceProvider else providerForNewTab()
    providers["tab-$tabCounter"] = provider
    val bridge = ComposeUiBridge(provider, deviceCatalog, viewSettingsStore)
    val controller = LogcatSessionController(provider, newLogStreamGateway(), preferencesStore, bridge)
    bridge.onOpenSession = { t, s -> openSessionTab(t, s) }
    bridge.bind(controller)
    controller.start()
    return LogcatTab("tab-$tabCounter", title, bridge, controller)
  }

  fun addTab() {
    val tab = createTab()
    tabs.add(tab)
    activeTabId = tab.id
  }

  fun closeTab(id: String) {
    val index = tabs.indexOfFirst { it.id == id }
    if (index < 0) return
    val tab = tabs[index]
    tab.controller?.stop()
    providers.remove(tab.id)
    tab.bridge.dispose()
    tabs.removeAt(index)
    if (tabs.isEmpty()) {
      addTab()
      return
    }
    if (activeTabId == id) {
      activeTabId = tabs[minOf(index, tabs.lastIndex)].id
    }
  }

  DisposableEffect(Unit) {
    val first = createTab()
    tabs.add(first)
    activeTabId = first.id
    McpRuntime.attach { tabs.firstOrNull { it.id == activeTabId }?.bridge }
    onDispose {
      McpRuntime.detach()
      tabs.forEach {
        it.controller?.stop()
        it.bridge.dispose()
      }
      if (GlobalContext.getOrNull() != null) {
        stopKoin()
      }
    }
  }

  Window(
    onCloseRequest = ::exitApplication,
    title = "LogCat by cactus",
    icon = painterResource("logcactus/icons/app-icon.png")
  ) {
    LogcatComposeTheme {
      Column(modifier = Modifier.fillMaxSize()) {
        LogcatTabBar(
          tabs = tabs,
          activeTabId = activeTabId,
          onSelect = { activeTabId = it },
          onClose = ::closeTab,
          onAdd = ::addTab
        )
        val active = tabs.firstOrNull { it.id == activeTabId }
        Box(modifier = Modifier.weight(1f)) {
          if (active != null) {
            // key: у каждой вкладки своё состояние (remember/скролл/фокус), а не общее с предыдущей.
            androidx.compose.runtime.key(active.id) { LogcatComposeScreen(active.bridge) }
          }
        }
      }
    }
  }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
private fun TabTip(text: String, content: @androidx.compose.runtime.Composable () -> Unit) {
  androidx.compose.foundation.TooltipArea(
    tooltip = {
      Box(
        modifier = Modifier
          .background(Color(0xFF3C3F41), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) { Text(text, color = TextMain, fontSize = 12.sp) }
    },
    delayMillis = 500,
    tooltipPlacement = androidx.compose.foundation.TooltipPlacement.CursorPoint(offset = androidx.compose.ui.unit.DpOffset(12.dp, 18.dp)),
    content = content
  )
}

@androidx.compose.runtime.Composable
private fun LogcatTabBar(
  tabs: List<LogcatTab>,
  activeTabId: String,
  onSelect: (String) -> Unit,
  onClose: (String) -> Unit,
  onAdd: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth().background(TabBarBg).padding(horizontal = 6.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    tabs.forEach { tab ->
      val active = tab.id == activeTabId
      Row(
        modifier = Modifier
          .background(if (active) TabActiveBg else TabBg, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
          .then(if (active) Modifier.background(Accent.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) else Modifier)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = tab.title,
          color = if (active) TextMain else TextMuted,
          fontSize = 12.sp,
          modifier = Modifier.clickable { onSelect(tab.id) }
        )
        if (tabs.size > 1) {
          TabTip("Close tab") {
          Button(
            onClick = { onClose(tab.id) },
            modifier = Modifier.width(16.dp).height(16.dp),
            contentPadding = PaddingValues(0.dp),
            elevation = null,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = TextMuted)
          ) {
            Icon(
              painter = painterResource("logcactus/icons/toolbar/close.svg"),
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.width(10.dp).height(10.dp)
            )
          }
          }
        }
      }
    }
    TabTip("New tab") {
    Button(
      onClick = onAdd,
      modifier = Modifier.width(24.dp).height(24.dp),
      contentPadding = PaddingValues(4.dp),
      elevation = null,
      colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = TextMuted)
    ) {
      Icon(
        painter = painterResource("logcactus/icons/toolbar/plus.svg"),
        contentDescription = null,
        tint = TextMuted,
        modifier = Modifier.width(14.dp).height(14.dp)
      )
    }
    }
  }
}
