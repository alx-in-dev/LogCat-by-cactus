package com.cactus.logcactus.plugin.ui

/**
 * Автодополнение строки фильтра (порт по духу LogcatFilterCompletionContributor, Apache-2.0): подсказки зависят от
 * того, где стоит курсор — на ключе или на значении `level:`/`is:`/`age:`/`tag:`/`package:`/`process:`.
 */
data class FilterSuggestion(val insert: String, val hint: String)

enum class CompletionKind { KEY, LEVEL, IS, AGE, TAG, PACKAGE, PROCESS, NONE }

data class CompletionContext(val kind: CompletionKind, val prefix: String, val replaceStart: Int, val replaceEnd: Int)

class KnownValues(val tags: List<String> = emptyList(), val packages: List<String> = emptyList(), val processes: List<String> = emptyList())

private val TOKEN_DELIMITERS = charArrayOf(' ', '(', ')', '&', '|', '!')
private val LEVEL_NAMES = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT")

private fun stringKey(key: String, hint: String) = listOf(
  FilterSuggestion("$key:", "$hint contains string"),
  FilterSuggestion("-$key:", "$hint does not contain string"),
  FilterSuggestion("$key~:", "$hint matches string"),
  FilterSuggestion("-$key~:", "$hint does not match string"),
  FilterSuggestion("$key=:", "$hint is exactly string"),
  FilterSuggestion("-$key=:", "$hint is not exactly string")
)

private val ALL_KEYS = listOf(
  FilterSuggestion("age:", "Filter by max age"),
  FilterSuggestion("is:", "Filter by type of log message"),
  FilterSuggestion("level:", "Filter by min log level"),
  FilterSuggestion("name:", "Specify a name for this filter"),
  FilterSuggestion("package:mine", "Filter logs from the app on the device screen")
) + stringKey("message", "Log message") + stringKey("package", "Package name") + stringKey("tag", "Log tag") +
  stringKey("process", "Process name") + stringKey("line", "Log line")

private val BASE_KEYS = listOf(
  FilterSuggestion("tag:", "Log tag contains string"),
  FilterSuggestion("age:", "Filter by max age"),
  FilterSuggestion("is:", "Filter by type of log message"),
  FilterSuggestion("level:", "Filter by min log level"),
  FilterSuggestion("line:", "Log line contains string"),
  FilterSuggestion("message:", "Log message contains string"),
  FilterSuggestion("package:", "Package name contains string"),
  FilterSuggestion("package:mine", "Filter logs from the app on the device screen"),
  FilterSuggestion("process:", "Process name contains string")
)

private val LEVEL_VALUES = LEVEL_NAMES.map { FilterSuggestion("$it ", "Filter by $it or higher") }
private val IS_VALUES = listOf(
  FilterSuggestion("crash ", "Filter crashes"),
  FilterSuggestion("firebase ", "Filter messages produced by Firebase libraries"),
  FilterSuggestion("stacktrace ", "Filter stack traces")
) + LEVEL_NAMES.map { FilterSuggestion("${it.lowercase()} ", "Filter level $it only") }
private val AGE_VALUES = listOf(
  FilterSuggestion("30s ", "Filter logs from past 30 seconds"),
  FilterSuggestion("5m ", "Filter logs from past 5 minutes"),
  FilterSuggestion("3h ", "Filter logs from past 3 hours"),
  FilterSuggestion("1d ", "Filter logs from past 1 day")
)

fun completionContextAt(text: String, cursor: Int): CompletionContext {
  val at = cursor.coerceIn(0, text.length)
  var start = at
  while (start > 0 && text[start - 1] !in TOKEN_DELIMITERS) start--
  val token = text.substring(start, at)
  val colon = token.indexOf(':')
  if (colon < 0) return CompletionContext(CompletionKind.KEY, token, start, at)
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
    else -> CompletionKind.NONE
  }
  val valueStart = start + colon + 1
  return CompletionContext(kind, text.substring(valueStart, at), valueStart, at)
}

fun suggestionsFor(ctx: CompletionContext, known: KnownValues, forceAll: Boolean): List<FilterSuggestion> {
  fun List<String>.asSuggestions() = filter { it.startsWith(ctx.prefix, ignoreCase = true) && !it.equals(ctx.prefix, true) }.take(40).map { FilterSuggestion("$it ", "") }
  fun List<FilterSuggestion>.byPrefix() = filter { it.insert.startsWith(ctx.prefix, ignoreCase = true) }
  return when (ctx.kind) {
    CompletionKind.KEY ->
      if (ctx.prefix.isEmpty()) (if (forceAll) BASE_KEYS else emptyList())
      else ALL_KEYS.filter { it.insert.startsWith(ctx.prefix, ignoreCase = true) && it.insert != ctx.prefix }
    CompletionKind.LEVEL -> LEVEL_VALUES.byPrefix()
    CompletionKind.IS -> IS_VALUES.byPrefix()
    CompletionKind.AGE -> AGE_VALUES.byPrefix()
    CompletionKind.TAG -> known.tags.asSuggestions()
    CompletionKind.PACKAGE -> {
      val mine = if ("mine".startsWith(ctx.prefix, ignoreCase = true) && !ctx.prefix.equals("mine", true)) listOf(FilterSuggestion("mine ", "Filter logs from the app on the device screen")) else emptyList()
      mine + known.packages.asSuggestions()
    }
    CompletionKind.PROCESS -> known.processes.asSuggestions()
    CompletionKind.NONE -> emptyList()
  }
}
