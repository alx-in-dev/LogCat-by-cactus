package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalComposeUiApi::class)
class CompareDialogRenderTest {
  @Test
  fun largeComparisonReportRendersWithoutErrors() {
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      javax.swing.SwingUtilities.invokeAndWait {
        val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
        bridge.compareReport = "## Session comparison\n\n" + (1..300).joinToString("\n") { "- `Tag$it: some long error message number $it that keeps going and going for a while` ×$it" }
        val scene = ImageComposeScene(1100, 900, Density(2f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        var t = 0L
        repeat(15) { t += 16_000_000; scene.render(t) }
        fun shot(name: String) = File("build/$name.png").writeBytes(scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        shot("compare-dialog")
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(550f, 450f)); t += 16_000_000; scene.render(t)
        repeat(60) {
          scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll, androidx.compose.ui.geometry.Offset(550f, 450f), scrollDelta = androidx.compose.ui.geometry.Offset(0f, 6f))
          repeat(3) { t += 16_000_000; scene.render(t) }
        }
        shot("compare-dialog-scrolled")
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }
}
