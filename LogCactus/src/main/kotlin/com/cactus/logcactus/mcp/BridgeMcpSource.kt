package com.cactus.logcactus.mcp

import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogcatFormatter
import com.cactus.logcactus.presentation.logcat.CrashGroup
import com.cactus.logcactus.presentation.logcat.compose.ComposeUiBridge
import javax.swing.SwingUtilities

/**
 * Источник данных MCP — активная вкладка окна. Чтение состояния идёт на Swing-потоке (мост живёт там), а долгие вызовы
 * (adb) — на потоке MCP, чтобы не вешать окно. [activeBridge] тоже вычисляется на Swing-потоке: вкладки — состояние Compose.
 */
class BridgeMcpSource(private val activeBridge: () -> ComposeUiBridge?) : McpLogcatSource {
  private val formatter = LogcatFormatter()

  private fun <T> ui(block: (ComposeUiBridge) -> T): T {
    var out: Result<T>? = null
    val run = { out = runCatching { block(activeBridge() ?: error("No active LogCactus tab")) } }
    if (SwingUtilities.isEventDispatchThread()) run() else SwingUtilities.invokeAndWait(run)
    return out!!.getOrThrow()
  }

  private fun bridge(): ComposeUiBridge = ui { it }

  override fun devices(): List<DeviceRef> = ui { it.devices }
  override fun selectedDeviceId(): String? = ui { it.selectedDevice?.id }
  override fun viewFilter() = ui { McpLogcatSource.ViewFilter(it.query, it.level, if (it.mineActive) it.followedPackage else "") }
  override fun entries(allBuffer: Boolean): List<LogEntry> = ui { if (allBuffer) it.bufferEntries() else it.visibleEntries() }
  override fun crashes(): List<CrashGroup> = ui { it.crashGroups }
  override fun bookmarkLines(): List<String> = ui { b -> b.bookmarks.map { it.text } }
  override fun bugReport(lines: List<String>, source: String): String = bridge().bugReportText(lines, source)
  override fun screenshot(): ByteArray? = bridge().screenshotPng()
}
