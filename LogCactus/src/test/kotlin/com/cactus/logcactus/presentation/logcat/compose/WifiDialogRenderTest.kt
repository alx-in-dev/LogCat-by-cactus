package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.MdnsService
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalComposeUiApi::class)
class WifiDialogRenderTest {
  @Test
  fun wifiDialogRendersWithoutErrors() {
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      javax.swing.SwingUtilities.invokeAndWait {
        val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
        bridge.selectedDevice = DeviceRef("c7e84296", "CPH2573")
        bridge.wifiServices = listOf(MdnsService("adb-ABC-xyz", "192.168.0.12:44444", true), MdnsService("adb-ABC-xyz", "192.168.0.12:37123", false))
        bridge.wifiDialog = true
        val scene = ImageComposeScene(1100, 800, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        var t = 0L
        repeat(12) { t += 16_000_000; scene.render(t) }
        File("build/wifi-dialog.png").writeBytes(scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }
}
