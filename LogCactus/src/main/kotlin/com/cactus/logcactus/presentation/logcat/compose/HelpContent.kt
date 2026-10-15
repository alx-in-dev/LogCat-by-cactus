package com.cactus.logcactus.presentation.logcat.compose

/** Текст справки (кнопка «?»): хоткеи, синтаксис фильтра, возможности. Держать в синхроне с README. */
object HelpContent {
  class Entry(val keys: String, val description: String)
  class Section(val title: String, val entries: List<Entry>)

  private fun e(keys: String, description: String) = Entry(keys, description)

  val sections: List<Section> = listOf(
    Section(
      "Keyboard shortcuts",
      listOf(
        e("Cmd/Ctrl + F", "Search in the log (folded stack traces are searched too)"),
        e("Enter / Shift+Enter", "Next / previous search match"),
        e("Esc", "Close search or the suggestions list"),
        e("Enter (filter field)", "Add the current filter to the history; the filter itself is applied as you type"),
        e("Ctrl + Space", "Show filter suggestions in the filter field"),
        e("↑ / ↓ / Enter", "Choose and apply a filter suggestion"),
        e("F2 / Shift + F2", "Jump to the next / previous bookmark"),
        e("Cmd/Ctrl + Shift + C", "Copy a bug report (device, app version, filter, log)"),
        e("Cmd/Ctrl + C", "Copy the selected text (drag with the mouse to select)")
      )
    ),
    Section(
      "Mouse",
      listOf(
        e("Click level badge", "Add / remove a bookmark on the line"),
        e("Right-click a line", "Copy, copy message, bug report around the line, bookmark, expand JSON / Intent / URL, search in Google, clear"),
        e("Click ▸ N stack frames", "Expand / collapse the stack trace"),
        e("Click a link", "Open the URL from the message in the browser"),
        e("JSON / Intent in a message", "Pretty-printed under the line automatically (also after a prefix like Body: and when logcat cut it off); switch off in Configure view or collapse one line from its right-click menu"),
        e("Long URL in a message", "http(s) links of 120+ characters with a query are split under the line: the address stays, every parameter goes on its own line as key = value (percent-escapes decoded); own switch «Auto-expand long URLs» in Configure view; same chevron and right-click menu as JSON"),
        e("Click the mine: chip", "Pin the app (stop following the app on screen) / follow again")
      )
    ),
    Section(
      "Filter syntax",
      listOf(
        e("tag: message: line: process: name:", "Substring match. Negate with -key: ; regex with key~: ; exact with key=:"),
        e("package: app:", "Package of the app. app: opens a picker with installed apps"),
        e("package:mine", "The app that is on the device screen right now; follows the app you switch to"),
        e("level:WARN", "Minimum level (VERBOSE, DEBUG, INFO, WARN, ERROR)"),
        e("is:crash  is:stacktrace", "Crashes, stack traces, firebase messages, or a single level (is:error)"),
        e("age:30s  age:5m  age:2h  age:1d", "Only logs newer than the given age"),
        e("! - & | ( )", "NOT, AND, OR and grouping: (tag:Net | tag:Db) & level:ERROR")
      )
    ),
    Section(
      "Side toolbar",
      listOf(
        e("Clear / Pause / Restart", "Clear the log, pause updates, restart the logcat stream"),
        e("Scroll to end", "Follow new logs automatically"),
        e("↑ ↓ arrows", "Previous / next stack frame"),
        e("Bookmark", "Next / previous bookmark, clear bookmarks"),
        e("Clipboard", "Copy bug report"),
        e("Warning triangle", "Crashes and ANR found in the log: grouped, click to jump, Copy for a report"),
        e("Wrap", "Soft-wrap long lines"),
        e("Phone", "Connect via Wi-Fi, screenshot, restart / force stop / clear cache / clear data, deep link, app info, sessions"),
        e("Sliders", "Configure view: process, level, columns, font size, fold stack traces, auto-expand JSON, buffer size")
      )
    ),
    Section(
      "Log buffer",
      listOf(
        e("Configure view → Buffer", "How much log history is kept in memory (512 KB … 8 MB, default 2 MB). When the limit is exceeded the oldest lines are dropped. A bigger buffer keeps more history but uses more memory and CPU"),
        e("Save session…", "Saves the buffer (or only what the filter shows) to a file, so a long history can be kept")
      )
    ),
    Section(
      "Wi-Fi debugging",
      listOf(
        e("Phone → Connect via Wi-Fi…", "Three ways: switch a USB phone to Wi-Fi, pair by code (Android 11+), or connect by IP:port; Scan finds phones on the network"),
        e("Phone → Disconnect this Wi-Fi device", "Shown when the selected device is connected over the network")
      )
    ),
    Section(
      "Sessions",
      listOf(
        e("Save session…", "Save to a .logcactus file (with device, app and filter): only what is visible by the current filter, or the whole buffer (last 5000 lines)"),
        e("Open session…", "Open a saved file in a new tab, no device needed"),
        e("Compare with session…", "Compare the current buffer with a saved baseline: new / gone errors, crashes, tags")
      )
    ),
    Section(
      "AI agents (MCP server)",
      listOf(
        e("What it is", "An optional local server that lets an AI agent (Claude Code, Cursor, Claude Desktop…) read what you see here: devices, filtered logs, crashes, a bug report, a screenshot. Read-only: it cannot restart, stop or clear anything. Off by default"),
        e("1. Turn on", "Phone → AI agents → «MCP server: off — turn on». It stays on after a restart. Or start the app with -Dlogcactus.mcp=true (port: -Dlogcactus.mcp.port, default 7788)"),
        e("2. Copy the address", "Phone → AI agents → «Copy MCP URL»: http://127.0.0.1:7788/mcp. It listens on this computer only; requests from other hosts or web pages are rejected"),
        e("3. Claude Code", "Run in a terminal: claude mcp add --transport http logcactus http://127.0.0.1:7788/mcp   (check with: claude mcp list)"),
        e("3. Cursor / other clients", "Add an HTTP (streamable) MCP server with the URL above. For clients that only run commands: npx mcp-remote http://127.0.0.1:7788/mcp"),
        e("Tools", "list_devices, get_logs (filter, level, scope visible|buffer, limit), get_crashes, get_bug_report (visible|crashes|bookmarks), take_screenshot. The filter uses the syntax above; the agent works with the active tab and does not change your filter"),
        e("Example", "«Open the app, go through checkout, then show me the errors from the last minute»: the agent drives the phone with its own tools (e.g. appium-mcp) and reads the log here with get_logs filter=\"package:mine level:E age:1m\""),
        e("Turn off", "Phone → AI agents → «MCP server: on — turn off». If the port is busy, the menu shows the reason; choose another port with -Dlogcactus.mcp.port")
      )
    )
  )

  /** Вся справка одним текстом (кнопка Copy). */
  fun asText(): String = buildString {
    for (s in sections) {
      append(s.title).append('\n')
      for (en in s.entries) append("  ").append(en.keys).append(" — ").append(en.description).append('\n')
      append('\n')
    }
  }.trimEnd() + "\n"
}
