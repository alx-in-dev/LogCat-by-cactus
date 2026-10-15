package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Открывает основные меню/панели и сохраняет снимки в build/ (для визуальной проверки), ловит исключения при открытии. */
@OptIn(ExperimentalComposeUiApi::class)
class MenusRenderTest {
  private fun shot(name: String, x: Float, y: Float) {
    val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
    bridge.devices = listOf(DeviceRef("c7e84296", "CPH2573"), DeviceRef("emulator-5554", "Pixel_7"))
    bridge.selectedDevice = bridge.devices.first()
    bridge.bufferLines = 2732
    val scene = ImageComposeScene(1000, 620, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
    var t = 0L
    fun frame() { t += 16_000_000; scene.render(t) }
    repeat(10) { frame() }
    scene.sendPointerEvent(PointerEventType.Move, Offset(x, y)); frame()
    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), button = PointerButton.Primary); frame()
    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), button = PointerButton.Primary); frame()
    repeat(20) { frame() }
    File("build/menu-$name.png").writeBytes(scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }

  @Test
  fun menusRenderWithoutErrors() {
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      javax.swing.SwingUtilities.invokeAndWait {
        shot("phone", 21f, 370f)
        shot("bookmarks", 21f, 250f)
        shot("configure", 21f, 410f)
        shot("device", 170f, 34f)
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }
}
