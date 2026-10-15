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
class SaveDialogRenderTest {
  @Test
  fun saveScopeDialogRenders() {
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      javax.swing.SwingUtilities.invokeAndWait {
        val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
        bridge.saveRequest = ComposeUiBridge.SaveRequest(allCount = 4982, visibleCount = 137)
        val scene = ImageComposeScene(900, 500, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        var t = 0L
        repeat(12) { t += 16_000_000; scene.render(t) }
        File("build/save-dialog.png").writeBytes(scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }
}
