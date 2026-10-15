package com.android.tools.idea.logcat.standalone.core

class LogcatUiState(
  val selectedDevice: DeviceRef?,
  val selectedClient: ClientRef?,
  val minLevel: String,
  val configuredFilterName: String,
  val query: String,
  val isRegex: Boolean,
  val isCaseSensitive: Boolean,
  // Список не копируется: экземпляры LogcatUiState неизменяемы, а все места, создающие
  // новый список (append/clearLogs), передают свежую копию, которую больше никто не меняет.
  val visibleLogs: List<LogEntry>,
  // Размер буфера в байтах (как "cycle buffer" оригинального Logcat): когда суммарный размер записей превышает
  // лимит, самые старые записи выбрасываются. Настраивается в Configure view.
  val maxBytes: Long = DEFAULT_BUFFER_BYTES,
  // Суммарный размер записей в visibleLogs; передаётся дальше без пересчёта (append/clearLogs его ведут сами).
  val totalBytes: Long = visibleLogs.sumOf { approxBytes(it) }
) {

  fun withSelection(device: DeviceRef?, client: ClientRef?): LogcatUiState {
    return LogcatUiState(device, client, minLevel, configuredFilterName, query, isRegex, isCaseSensitive, visibleLogs, maxBytes, totalBytes)
  }

  fun withFilter(
    nextLevel: String,
    nextFilterName: String,
    nextQuery: String,
    nextRegex: Boolean,
    nextCaseSensitive: Boolean
  ): LogcatUiState {
    return LogcatUiState(selectedDevice, selectedClient, nextLevel, nextFilterName, nextQuery, nextRegex, nextCaseSensitive, visibleLogs, maxBytes, totalBytes)
  }

  /** Меняет лимит буфера; при уменьшении сразу выбрасывает самые старые записи. */
  fun withBufferLimit(limitBytes: Long): LogcatUiState {
    val limit = limitBytes.coerceAtLeast(MIN_BUFFER_BYTES)
    var drop = 0
    var total = totalBytes
    while (total > limit && drop < visibleLogs.size - 1) {
      total -= approxBytes(visibleLogs[drop])
      drop++
    }
    val logs = if (drop == 0) visibleLogs else ArrayList(visibleLogs.subList(drop, visibleLogs.size))
    return LogcatUiState(selectedDevice, selectedClient, minLevel, configuredFilterName, query, isRegex, isCaseSensitive, logs, limit, total)
  }

  /**
   * Добавляет запись. Когда буфер переполняется, сначала выбрасываются записи, НЕ видимые под текущим фильтром
   * ([keep] — предикат «запись показывается»; вычисляется только при переполнении), и лишь потом самые старые видимые.
   * Иначе при `package:mine` шум других процессов вытеснял бы редкие записи приложения, и они пропадали бы из окна,
   * пока приложение молчит. Освобождается запас в 10% лимита за проход, чтобы не пересобирать список на каждую запись.
   */
  fun append(entry: LogEntry, keep: (() -> ((LogEntry) -> Boolean)?)? = null): LogcatUiState {
    val size = approxBytes(entry)
    var total = totalBytes + size
    if ((total > maxBytes || visibleLogs.size + 1 > MAX_ENTRIES) && keep != null && visibleLogs.isNotEmpty()) {
      keep()?.let { visible -> return appendEvictingInvisible(entry, size, visible) }
    }
    var drop = 0
    // Старые записи выбрасываются, пока не влезем в лимит (и не превысим разумный потолок по числу записей).
    while ((total > maxBytes || visibleLogs.size - drop >= MAX_ENTRIES) && drop < visibleLogs.size) {
      total -= approxBytes(visibleLogs[drop])
      drop++
    }
    // Одна копия на запись (раньше было две + сдвиг массива в removeAt(0)).
    val next = ArrayList<LogEntry>(visibleLogs.size - drop + 1)
    if (drop == 0) next.addAll(visibleLogs) else next.addAll(visibleLogs.subList(drop, visibleLogs.size))
    next.add(entry)
    return LogcatUiState(selectedDevice, selectedClient, minLevel, configuredFilterName, query, isRegex, isCaseSensitive, next, maxBytes, total)
  }

  private fun appendEvictingInvisible(entry: LogEntry, size: Long, visible: (LogEntry) -> Boolean): LogcatUiState {
    val targetBytes = (maxBytes * 0.9).toLong()
    val targetCount = (MAX_ENTRIES * 0.9).toInt()
    val n = visibleLogs.size
    val victim = BooleanArray(n)
    var total = totalBytes + size
    var count = n + 1
    // 1) невидимые под фильтром, от старых к новым
    for (i in 0 until n) {
      if (total <= targetBytes && count <= targetCount) break
      if (!visible(visibleLogs[i])) { victim[i] = true; total -= approxBytes(visibleLogs[i]); count-- }
    }
    // 2) если этого мало — самые старые из оставшихся (видимые)
    var i = 0
    // Тоже до запаса в 10%: иначе, когда одни видимые записи занимают весь буфер, дорогой проход по фильтру шёл бы на каждую запись.
    while ((total > targetBytes || count > targetCount) && i < n) {
      if (!victim[i]) { victim[i] = true; total -= approxBytes(visibleLogs[i]); count-- }
      i++
    }
    val next = ArrayList<LogEntry>(count)
    for (k in 0 until n) if (!victim[k]) next.add(visibleLogs[k])
    next.add(entry)
    return LogcatUiState(selectedDevice, selectedClient, minLevel, configuredFilterName, query, isRegex, isCaseSensitive, next, maxBytes, total)
  }

  fun clearLogs(): LogcatUiState {
    return LogcatUiState(selectedDevice, selectedClient, minLevel, configuredFilterName, query, isRegex, isCaseSensitive, emptyList(), maxBytes, 0L)
  }

  companion object {
    const val MIN_BUFFER_BYTES = 256L * 1024
    const val DEFAULT_BUFFER_BYTES = 2L * 1024 * 1024
    private const val MAX_ENTRIES = 200_000
    // Накладные расходы на запись сверх текста (объект, ссылки, строки): чтобы лимит в МБ отражал реальную память.
    private const val ENTRY_OVERHEAD = 48

    @JvmStatic
    fun approxBytes(entry: LogEntry): Long =
      (entry.message.length + entry.tag.length + entry.processName.length + entry.packageName.length + ENTRY_OVERHEAD).toLong()

    @JvmStatic
    fun initial(preferences: LogcatUiPreferences): LogcatUiState {
      return LogcatUiState(
        null,
        null,
        preferences.minLevel,
        preferences.configuredFilterName,
        preferences.query,
        preferences.isRegex,
        preferences.isCaseSensitive,
        emptyList(),
        preferences.bufferBytes.coerceAtLeast(MIN_BUFFER_BYTES),
        0L
      )
    }
  }
}
