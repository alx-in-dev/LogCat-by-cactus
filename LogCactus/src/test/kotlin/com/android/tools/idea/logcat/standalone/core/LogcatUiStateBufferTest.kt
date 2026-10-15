package com.android.tools.idea.logcat.standalone.core

import com.cactus.logcactus.standalone.FilePreferencesStore
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogcatUiStateBufferTest {
  private fun entry(i: Int, len: Int = 100) = LogEntry(i.toLong(), "INFO", "T", 1, "p", "com.a", "x".repeat(len) + i)

  private fun empty(limit: Long) =
    LogcatUiState(null, null, "VERBOSE", "", "", true, false, emptyList(), limit, 0L)

  @Test
  fun oldestEntriesAreDroppedWhenBytesExceedLimit() {
    val one = LogcatUiState.approxBytes(entry(1))
    var s = empty(one * 10 + one / 2) // влезает 10 записей
    for (i in 1..25) s = s.append(entry(i))
    assertEquals(10, s.visibleLogs.size)
    assertEquals(16L, s.visibleLogs.first().timestamp)
    assertEquals(25L, s.visibleLogs.last().timestamp)
    assertTrue(s.totalBytes <= s.maxBytes)
    assertEquals(s.visibleLogs.sumOf { LogcatUiState.approxBytes(it) }, s.totalBytes)
  }

  @Test
  fun bigEntryEvictsSeveralSmallOnesButIsKept() {
    val small = LogcatUiState.approxBytes(entry(1, 10))
    var s = empty(small * 5)
    for (i in 1..5) s = s.append(entry(i, 10))
    s = s.append(entry(99, 10 * 4)) // крупнее: вытесняет больше одной
    assertEquals(99L, s.visibleLogs.last().timestamp)
    assertTrue(s.visibleLogs.size < 5)
    assertTrue(s.totalBytes <= s.maxBytes)
    // одна запись больше всего лимита — всё равно остаётся (пусто быть не должно)
    val huge = empty(LogcatUiState.MIN_BUFFER_BYTES).append(entry(1, 400_000))
    assertEquals(1, huge.visibleLogs.size)
  }

  @Test
  fun shrinkingLimitTrimsImmediatelyAndGrowingKeepsEverything() {
    var s = empty(10L * 1024 * 1024)
    for (i in 1..3000) s = s.append(entry(i))
    val grown = s.withBufferLimit(20L * 1024 * 1024)
    assertSame(s.visibleLogs, grown.visibleLogs)
    val shrunk = s.withBufferLimit(LogcatUiState.MIN_BUFFER_BYTES)
    assertTrue(shrunk.visibleLogs.size < 3000)
    assertEquals(3000L, shrunk.visibleLogs.last().timestamp)
    assertTrue(shrunk.totalBytes <= LogcatUiState.MIN_BUFFER_BYTES)
  }

  @Test
  fun clearAndFilterKeepLimitAndResetOrKeepTotal() {
    var s = empty(1L * 1024 * 1024).append(entry(1))
    assertEquals(1L * 1024 * 1024, s.withFilter("WARN", "", "q", false, true).maxBytes)
    assertFalse(s.clearLogs().totalBytes > 0)
    assertEquals(s.totalBytes, s.withSelection(null, null).totalBytes)
  }

  @Test
  fun bufferSizeIsPersistedInPreferences() {
    val dir = Files.createTempDirectory("logcactus-prefs")
    val file = dir.resolve("preferences.properties")
    try {
      val store = FilePreferencesStore(file)
      assertEquals(LogcatUiState.DEFAULT_BUFFER_BYTES, store.load().bufferBytes)
      store.save(LogcatUiPreferences("WARN", "", "tag:x", true, false, 8L * 1024 * 1024))
      assertEquals(8L * 1024 * 1024, FilePreferencesStore(file).load().bufferBytes)
      assertEquals(8L * 1024 * 1024, LogcatUiState.initial(FilePreferencesStore(file).load()).maxBytes)
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(dir)
    }
  }

  @Test
  fun overflowEvictsEntriesHiddenByTheFilterBeforeVisibleOnes() {
    fun app(i: Int) = LogEntry(i.toLong(), "INFO", "T", 1, "p", "com.mine", "x".repeat(100) + i)
    fun noise(i: Int) = LogEntry(i.toLong(), "INFO", "T", 2, "q", "com.other", "x".repeat(100) + i)
    val one = LogcatUiState.approxBytes(app(1))
    var s = empty(one * 20)
    val visible: () -> ((LogEntry) -> Boolean)? = { { e -> e.packageName == "com.mine" } }
    for (i in 1..5) s = s.append(app(i), visible)                // редкие записи приложения
    for (i in 100..400) s = s.append(noise(i), visible)          // много шума от других процессов
    val mine = s.visibleLogs.filter { it.packageName == "com.mine" }
    assertEquals(listOf(1L, 2L, 3L, 4L, 5L), mine.map { it.timestamp }, "app entries must survive the noise")
    assertTrue(s.totalBytes <= s.maxBytes)
    assertEquals(s.visibleLogs.sumOf { LogcatUiState.approxBytes(it) }, s.totalBytes)
  }

  @Test
  fun whenEverythingIsVisibleTheOldestAreStillDropped() {
    val one = LogcatUiState.approxBytes(entry(1))
    var s = empty(one * 10 + one / 2)
    val all: () -> ((LogEntry) -> Boolean)? = { { true } }
    for (i in 1..25) s = s.append(entry(i), all)
    assertEquals(25L, s.visibleLogs.last().timestamp)
    assertTrue(s.visibleLogs.size in 8..10)
    assertTrue(s.totalBytes <= s.maxBytes)
  }

  @Test
  fun whenVisibleEntriesAloneFillTheBufferNoiseIsDroppedAndOldestVisibleGoToo() {
    fun app(i: Int) = LogEntry(i.toLong(), "INFO", "T", 1, "p", "com.mine", "x".repeat(100) + i)
    fun noise(i: Int) = LogEntry(i.toLong(), "INFO", "T", 2, "q", "com.other", "x".repeat(100) + i)
    val one = LogcatUiState.approxBytes(app(1))
    var s = empty(one * 20)
    val visible: () -> ((LogEntry) -> Boolean)? = { { e -> e.packageName == "com.mine" } }
    for (i in 1..200) { s = s.append(app(i), visible); s = s.append(noise(1000 + i), visible) }
    val mine = s.visibleLogs.filter { it.packageName == "com.mine" }
    assertTrue(s.totalBytes <= s.maxBytes)
    assertEquals(200L, mine.last().timestamp, "newest app entry is kept")
    assertTrue(mine.size >= 14, "most of the buffer is app entries: ${mine.size}")
    assertTrue(s.visibleLogs.size - mine.size <= 5, "noise holds almost no space")
    assertEquals(s.visibleLogs.sumOf { LogcatUiState.approxBytes(it) }, s.totalBytes)
  }
}
