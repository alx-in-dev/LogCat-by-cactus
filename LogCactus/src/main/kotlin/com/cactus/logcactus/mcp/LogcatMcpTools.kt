package com.cactus.logcactus.mcp

import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogQueryMatcher
import com.android.tools.idea.logcat.standalone.core.LogcatFormatter
import com.cactus.logcactus.presentation.logcat.CrashGroup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Что инструменты знают о приложении. Реализация ходит в UI-поток сама; здесь только чтение (запись и действия над устройством — не в прототипе). */
interface McpLogcatSource {
  fun devices(): List<DeviceRef>
  fun selectedDeviceId(): String?
  /** Фильтр и уровень, которые сейчас выставлены в окне (инструменты их не меняют). */
  fun viewFilter(): ViewFilter
  /** Записи, прошедшие фильтр окна, либо весь буфер. */
  fun entries(allBuffer: Boolean): List<LogEntry>
  fun crashes(): List<CrashGroup>
  fun bookmarkLines(): List<String>
  /** Блокирующий вызов (adb): собирает Markdown-отчёт так же, как кнопка «Copy bug report». */
  fun bugReport(lines: List<String>, source: String): String
  fun screenshot(): ByteArray?

  class ViewFilter(val query: String, val level: String, val minePackage: String)
}

/** Набор инструментов LogCactus MCP. Все — только чтение. */
class LogcatMcpTools(private val source: McpLogcatSource) {
  private val formatter = LogcatFormatter()
  private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

  fun all(): List<McpTool> = listOf(listDevices(), getLogs(), getCrashes(), getBugReport(), takeScreenshot())

  private fun schema(vararg props: Triple<String, String, String>, required: List<String> = emptyList()) = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
      props.forEach { (name, type, desc) -> put(name, buildJsonObject { put("type", type); put("description", desc) }) }
    })
    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
  }

  private fun listDevices() = McpTool(
    "list_devices",
    "List Android devices known to LogCactus and the filter currently set in the app window (which get_logs uses with scope=visible).",
    schema()
  ) {
    val selected = source.selectedDeviceId()
    val devices = source.devices()
    val view = source.viewFilter()
    val sb = StringBuilder()
    if (devices.isEmpty()) sb.append("No devices.\n")
    devices.forEach { d ->
      sb.append(if (d.id == selected) "* " else "  ").append(d.id).append(" — ").append(d.displayName)
      if (d.androidVersion.isNotBlank()) sb.append(", Android ").append(d.androidVersion)
      if (d.apiLevel.isNotBlank()) sb.append(" (API ").append(d.apiLevel).append(')')
      sb.append(if (d.isEmulator) ", emulator" else ", physical").append('\n')
    }
    sb.append("(* = device of the active tab)\nWindow filter: ").append(view.query.ifBlank { "<none>" }).append(", level ≥ ").append(view.level)
    McpToolResult.text(sb.toString())
  }

  private fun getLogs() = McpTool(
    "get_logs",
    "Read recent log lines (the newest `limit`) from the active LogCactus tab. `filter` uses the LogCactus/Android Studio Logcat " +
      "query language: `tag:Foo`, `package:com.app`, `package:mine` (app on the device screen), `level:W` (warn and above), " +
      "`message:text`, `age:30s`, `is:crash`, `-tag:Noise` (negation), `tag:a | tag:b`, \"quoted text\". Bare words match the message.",
    schema(
      Triple("filter", "string", "Query on top of the scope. Optional."),
      Triple("level", "string", "Minimum level: VERBOSE, DEBUG, INFO, WARN, ERROR. Optional."),
      Triple("scope", "string", "`visible` (default): lines passing the filter set in the app window. `buffer`: the whole buffer, ignoring it."),
      Triple("limit", "integer", "Max lines, default 200, max $MAX_LINES.")
    )
  ) { args ->
    val scopeAll = args.string("scope") == "buffer"
    val view = source.viewFilter()
    val query = listOfNotNull(args.string("level")?.takeIf { it.isNotBlank() }?.let { "level:$it" }, args.string("filter")).joinToString(" ")
    val limit = (args.int("limit") ?: DEFAULT_LINES).coerceIn(1, MAX_LINES)
    val base = source.entries(scopeAll)
    // regex=false: агент пишет обычный текст; регулярное выражение — явно через `message~:`.
    val matcher = LogQueryMatcher(query, regex = false, caseSensitive = false, minePackage = view.minePackage)
    val matched = if (query.isBlank()) base else base.filter(matcher::matches)
    val tail = matched.takeLast(limit)
    val lines = tail.map { line(it) }
    val (body, cut) = fitToBudget(lines)
    val header = "${lines.size - cut} of ${matched.size} matching lines (newest last; buffer has ${base.size}; scope=${if (scopeAll) "buffer" else "visible"})" +
      if (cut > 0) " — $cut oldest dropped to fit the size limit, narrow the filter" else ""
    McpToolResult.text(header + "\n" + body)
  }

  private fun getCrashes() = McpTool(
    "get_crashes",
    "Java crashes, ANRs and native crashes found in the buffer, grouped by signature, newest first, with the stack trace of one occurrence.",
    schema()
  ) {
    val groups = source.crashes()
    if (groups.isEmpty()) McpToolResult.text("No crashes or ANRs in the buffer.")
    else McpToolResult.text(groups.joinToString("\n\n") { g ->
      "#### ${g.kind} ×${g.count}: ${g.title}\n" +
        "package: ${g.packageName.ifBlank { "?" }}, first ${time.format(Instant.ofEpochMilli(g.firstSeen))}, last ${time.format(Instant.ofEpochMilli(g.lastSeen))}\n" +
        g.sample.take(MAX_STACK_LINES).joinToString("\n") + if (g.sample.size > MAX_STACK_LINES) "\n… ${g.sample.size - MAX_STACK_LINES} more lines" else ""
    })
  }

  private fun getBugReport() = McpTool(
    "get_bug_report",
    "Build a ready-to-paste Markdown bug report (device, Android version, app version, filter, log lines) like the app's \"Copy bug report\".",
    schema(Triple("mode", "string", "`visible` (default): last 200 visible lines. `crashes`: stack traces of all crash groups. `bookmarks`: lines bookmarked in the app."))
  ) { args ->
    val (lines, label) = when (val mode = args.string("mode") ?: "visible") {
      "crashes" -> source.crashes().flatMap { it.sample } to "crashes"
      "bookmarks" -> source.bookmarkLines() to "bookmarked"
      "visible" -> source.entries(false).takeLast(REPORT_LINES).map { line(it) } to "last visible"
      else -> return@McpTool McpToolResult.text("Unknown mode '$mode'. Use visible, crashes or bookmarks.", isError = true)
    }
    if (lines.isEmpty()) McpToolResult.text("Nothing to report for mode '${args.string("mode") ?: "visible"}': no lines.", isError = true)
    else McpToolResult.text(source.bugReport(lines, label))
  }

  private fun takeScreenshot() = McpTool(
    "take_screenshot",
    "Screenshot of the device selected in the active tab (PNG). Does not touch the user's clipboard or files.",
    schema()
  ) {
    val png = source.screenshot()
    if (png == null) McpToolResult.text("Screenshot failed: no device selected or adb screencap returned nothing.", isError = true)
    else McpToolResult.image(png, "Screenshot of ${source.selectedDeviceId()}")
  }

  private fun line(e: LogEntry) = formatter.format(e)

  /** Обрезает с головы до [MAX_CHARS] символов: агенту важнее новые строки. Возвращает текст и число отброшенных строк. */
  private fun fitToBudget(lines: List<String>): Pair<String, Int> {
    var total = 0
    var from = lines.size
    while (from > 0 && total + lines[from - 1].length + 1 <= MAX_CHARS) { from--; total += lines[from].length + 1 }
    // Одна гигантская строка не помещается — отдаём её обрезанной, а не пустой ответ.
    if (from == lines.size && lines.isNotEmpty()) return lines.last().take(MAX_CHARS) to lines.size - 1
    return lines.subList(from, lines.size).joinToString("\n") to from
  }

  private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
  private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

  companion object {
    const val DEFAULT_LINES = 200
    const val MAX_LINES = 2000
    private const val MAX_CHARS = 60_000
    private const val MAX_STACK_LINES = 60
    private const val REPORT_LINES = 200
  }
}
