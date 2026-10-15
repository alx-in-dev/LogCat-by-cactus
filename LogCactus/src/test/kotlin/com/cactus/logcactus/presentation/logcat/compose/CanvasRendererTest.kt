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
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Прототип Canvas-рендера: список рисуется без Compose-узлов на строку, скролл колесом выключает слежение за концом. */
@OptIn(ExperimentalComposeUiApi::class)
class CanvasRendererTest {
  @Test
  fun canvasListDrawsRowsAndScrolls() {
    System.setProperty("logcactus.canvas", "true")
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      val entries = (1..400).map {
        LogEntry(1000L + it, if (it % 7 == 0) "ERROR" else "INFO", "Tag${it % 9}", 7, "p", "com.a", "line $it " + "word ".repeat(it % 30))
      }
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      assertTrue(bridge.canvasMode)
      bridge.loadSession(SessionFile.Session(emptyMap(), entries))
      repeat(60) { if (bridge.canvasRows.size < entries.size) Thread.sleep(50) }
      assertTrue(bridge.canvasRows.size == entries.size)
      var before = ByteArray(0)
      var after = ByteArray(0)
      var followAfter = true
      javax.swing.SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(1000, 600, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        var t = 0L
        fun frame() { t += 16_000_000; scene.render(t) }
        fun shot() = scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
        repeat(20) { frame() }
        before = shot()
        File("build/canvas-list.png").writeBytes(before)
        scene.sendPointerEvent(PointerEventType.Move, Offset(400f, 300f)); frame()
        repeat(5) { scene.sendPointerEvent(PointerEventType.Scroll, Offset(400f, 300f), scrollDelta = Offset(0f, -8f)); repeat(6) { frame() } }
        after = shot()
        File("build/canvas-list-scrolled.png").writeBytes(after)
        followAfter = bridge.follow
        scene.close()
      }
      System.setProperty("logcactus.canvas", "false")
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
      assertFalse(before.contentEquals(after), "canvas list did not move after a wheel scroll")
      assertFalse(followAfter, "scrolling up should stop following the tail")
    } finally {
      System.setProperty("logcactus.canvas", "false")
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }
}
