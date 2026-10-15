package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия: при выключенном переносе строк горизонтальный скролл должен двигать весь список целиком, в том
 * числе когда среди длинных строк есть короткие (раньше общий ScrollState брал maxValue у последней измеренной).
 */
@OptIn(ExperimentalComposeUiApi::class)
class HorizontalScrollTest {
  @Test
  fun wheelScrollShiftsContentEvenWithShortRowsMixedIn() {
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      val long = "word ".repeat(80)
      val entries = (1..12).map { i ->
        LogEntry(1000L + i, "INFO", "Tag$i", 7, "p", "com.a", if (i % 3 == 0) "short $i" else "line $i $long end$i")
      }
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      bridge.loadSession(SessionFile.Session(emptyMap(), entries))
      bridge.follow = false
      bridge.wrapLines = false
      repeat(60) { if (bridge.visibleRows.size < entries.size) Thread.sleep(50) }
      var before = ByteArray(0)
      var after = ByteArray(0)
      javax.swing.SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(1000, 400, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        var t = 0L
        fun frame() { t += 16_000_000; scene.render(t) }
        fun shot() = scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
        repeat(40) { frame() }
        before = shot()
        scene.sendPointerEvent(PointerEventType.Move, Offset(400f, 100f)); frame()
        repeat(4) { scene.sendPointerEvent(PointerEventType.Scroll, Offset(400f, 100f), scrollDelta = Offset(3f, 0f)); repeat(8) { frame() } }
        repeat(10) { frame() }
        after = shot()
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
      assertFalse(before.contentEquals(after), "content did not move after a horizontal wheel scroll")
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }
}
