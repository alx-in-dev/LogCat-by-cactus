package com.cactus.logcactus.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cactus.logcactus.presentation.logcat.compose.ComposeUiBridge
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Жизненный цикл встроенного MCP-сервера. По умолчанию выключен: включается пунктом меню (запоминается файлом
 * `~/.logcactus/mcp.enabled`) или `-Dlogcactus.mcp=true`. Порт — `-Dlogcactus.mcp.port`, по умолчанию 7788.
 */
object McpRuntime {
  const val DEFAULT_PORT = 7788
  private val marker: Path = Paths.get(System.getProperty("user.home"), ".logcactus", "mcp.enabled")
  private var server: McpHttpServer? = null
  private var activeBridge: () -> ComposeUiBridge? = { null }

  var running by mutableStateOf(false)
    private set
  var port by mutableStateOf(DEFAULT_PORT)
    private set
  var lastError by mutableStateOf<String?>(null)
    private set

  val url: String get() = "http://127.0.0.1:$port/mcp"

  /** Окно приложения сообщает, как найти активную вкладку; при необходимости сразу запускает сервер. */
  fun attach(activeBridge: () -> ComposeUiBridge?) {
    this.activeBridge = activeBridge
    if (System.getProperty("logcactus.mcp")?.equals("true", ignoreCase = true) == true || Files.exists(marker)) start()
  }

  fun detach() {
    stop()
    activeBridge = { null }
  }

  fun setEnabled(on: Boolean) {
    if (on) {
      if (start()) runCatching { Files.createDirectories(marker.parent); if (!Files.exists(marker)) Files.createFile(marker) }
    } else {
      stop()
      runCatching { Files.deleteIfExists(marker) }
    }
  }

  @Synchronized
  private fun start(): Boolean {
    if (server != null) return true
    val wanted = System.getProperty("logcactus.mcp.port")?.toIntOrNull() ?: DEFAULT_PORT
    return try {
      val protocol = McpProtocol(
        "logcactus", "1",
        "LogCactus gives read-only access to the Android logcat the user is looking at: devices, filtered log lines, crashes, bug reports, screenshots.",
        LogcatMcpTools(BridgeMcpSource { activeBridge() }).all()
      )
      val s = McpHttpServer(protocol, wanted)
      s.start()
      server = s
      port = s.port
      lastError = null
      running = true
      true
    } catch (e: Exception) {
      // Порт занят и т.п. — приложение работает дальше, причина видна в меню.
      lastError = "MCP server did not start on port $wanted: ${e.message}"
      running = false
      false
    }
  }

  @Synchronized
  private fun stop() {
    server?.stop()
    server = null
    running = false
  }
}
