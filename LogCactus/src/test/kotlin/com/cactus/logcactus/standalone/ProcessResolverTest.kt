package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessResolverTest {
  private fun line(pid: Int) = LogEntry(0L, "INFO", "CTC", pid, "msg")

  @Test
  fun aNewlyStartedAppGetsItsPackageOnTheVeryFirstLine() {
    val table = HashMap<Int, String>().apply { put(100, "system_server") }
    val resolver = AdbLogStreamGateway.ProcessMetadataResolver { HashMap(table) }
    assertEquals("", resolver.enrich(line(100)).packageName) // системный процесс без точки в имени
    // Приложение перезапустилось: новый pid появляется в ps уже ПОСЛЕ первого обновления таблицы.
    table[4242] = "com.ctcmediagroup.ctc"
    val first = resolver.enrich(line(4242))
    assertEquals("com.ctcmediagroup.ctc", first.packageName)
  }

  @Test
  fun aFloodOfDeadPidsDoesNotCallPsForEveryLine() {
    var calls = 0
    val resolver = AdbLogStreamGateway.ProcessMetadataResolver { calls++; emptyMap() }
    repeat(500) { resolver.enrich(line(1000 + it)) }
    assertTrue(calls <= 9, "ps calls = $calls")
  }
}
