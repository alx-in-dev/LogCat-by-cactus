package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalComposeUiApi::class)
class HelpDialogTest {
  @Test
  fun helpTextMentionsKeyShortcuts() {
    val text = HelpContent.asText()
    listOf("F2", "Cmd/Ctrl + F", "Cmd/Ctrl + Shift + C", "package:mine", "Ctrl + Space", "claude mcp add", "AI agents").forEach { assertTrue(it in text, "help lacks $it") }
  }

  @Test
  fun helpButtonOpensDialogWithoutErrors() {
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      // Вся сцена живёт в EDT: состояние моста меняется из Dispatchers.Swing, иначе Recomposer блокируется.
      javax.swing.SwingUtilities.invokeAndWait { runScene() }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  private fun runScene() {
    run {
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      val scene = ImageComposeScene(1100, 800, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
      var t = 0L
      fun frame() { t += 16_000_000; scene.render(t) }
      repeat(10) { frame() }
      // кнопка «?» правее звезды в поле фильтра (см. снимок repro0: ~779,34 при ширине 1000)
      for (x in listOf(779f, 880f)) {
        scene.sendPointerEvent(PointerEventType.Move, Offset(x, 34f)); frame()
        scene.sendPointerEvent(PointerEventType.Press, Offset(x, 34f), button = PointerButton.Primary); frame()
        scene.sendPointerEvent(PointerEventType.Release, Offset(x, 34f), button = PointerButton.Primary); frame()
      }
      repeat(10) { frame() }
      File("build/help-dialog.png").writeBytes(scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
      scene.close()
    }
  }
}
