package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Выделение и копирование в Canvas-списке (прототип): тянем мышью по строкам и копируем Cmd+C в подменённый буфер. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CanvasSelectionTest {
  private class FakeClipboard : ClipboardManager {
    var text: String? = null
    override fun getText(): AnnotatedString? = text?.let { AnnotatedString(it) }
    override fun setText(annotatedString: AnnotatedString) { text = annotatedString.text }
  }

  private fun withScene(block: (scene: ImageComposeScene, frame: () -> Unit, bridge: ComposeUiBridge, clipboard: FakeClipboard) -> Unit) {
    System.setProperty("logcactus.canvas", "true")
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      val entries = (1..40).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "message number $it alpha beta gamma") }
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      bridge.loadSession(SessionFile.Session(emptyMap(), entries))
      repeat(60) { if (bridge.canvasRows.size < entries.size) Thread.sleep(50) }
      val clipboard = FakeClipboard()
      javax.swing.SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(1000, 600, Density(1f)) {
          CompositionLocalProvider(LocalClipboardManager provides clipboard) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        }
        var t = 0L
        val frame = { t += 16_000_000; scene.render(t); Unit }
        repeat(20) { frame() }
        block(scene, frame, bridge, clipboard)
        File("build/canvas-selection.png").writeBytes(scene.render(t + 16_000_000).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      System.setProperty("logcactus.canvas", "false")
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  private fun ImageComposeScene.mouse(type: PointerEventType, x: Float, y: Float, frame: () -> Unit, button: PointerButton? = null) {
    sendPointerEvent(type, Offset(x, y), button = button)
    frame()
  }

  // Конструктор KeyEvent(key, ...) — внутренний API compose-ui (opt-in выше); публичного способа послать клавишу в сцену нет.
  private fun ImageComposeScene.key(key: Key, meta: Boolean, frame: () -> Unit) {
    sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isMetaPressed = meta))
    frame()
  }

  private fun ImageComposeScene.copy(frame: () -> Unit) = key(Key.C, meta = true, frame)

  @Test
  fun dragAcrossRowsCopiesVisibleText() = withScene { scene, frame, _, clipboard ->
    // Строки внизу списка (следим за концом): тянем от середины предпоследней до середины последней строки
    scene.mouse(PointerEventType.Move, 300f, 560f, frame)
    scene.mouse(PointerEventType.Press, 300f, 560f, frame, PointerButton.Primary)
    scene.mouse(PointerEventType.Move, 500f, 575f, frame)
    scene.mouse(PointerEventType.Move, 700f, 585f, frame)
    scene.mouse(PointerEventType.Release, 700f, 585f, frame, PointerButton.Primary)
    File("build/canvas-selection-drag.png").writeBytes(scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    scene.copy(frame)
    val text = clipboard.text
    assertTrue(!text.isNullOrEmpty(), "nothing was copied")
    assertTrue("message number" in text!! || "alpha" in text || "beta" in text, text)
    assertTrue('\n' in text || text.length > 5, text)
  }

  @Test
  fun doubleClickSelectsWordAndTripleClickSelectsLine() = withScene { scene, frame, _, clipboard ->
    fun click(count: Int) {
      val source = java.awt.Panel()
      val native = java.awt.event.MouseEvent(source, java.awt.event.MouseEvent.MOUSE_PRESSED, 0L, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 400, 580, count, false, java.awt.event.MouseEvent.BUTTON1)
      scene.sendPointerEvent(PointerEventType.Press, Offset(400f, 580f), button = PointerButton.Primary, nativeEvent = native)
      frame()
      scene.sendPointerEvent(PointerEventType.Release, Offset(400f, 580f), button = PointerButton.Primary, nativeEvent = native)
      frame()
    }
    scene.mouse(PointerEventType.Move, 400f, 580f, frame)
    click(2)
    scene.copy(frame)
    val word = clipboard.text.orEmpty()
    assertTrue(word.isNotBlank() && ' ' !in word.trim() && word.length < 20, "double click should select one word, was '$word'")
    click(3)
    scene.copy(frame)
    val line = clipboard.text.orEmpty()
    assertTrue("message number 40" in line && "gamma" in line, "triple click should select the whole row, was '$line'")
  }

  @Test
  fun selectAllCopiesEveryRowAndEscapeClears() = withScene { scene, frame, bridge, clipboard ->
    scene.mouse(PointerEventType.Move, 400f, 300f, frame)
    scene.mouse(PointerEventType.Press, 400f, 300f, frame, PointerButton.Primary)
    scene.mouse(PointerEventType.Release, 400f, 300f, frame, PointerButton.Primary)
    scene.key(Key.A, meta = true, frame)
    scene.copy(frame)
    val all = clipboard.text.orEmpty()
    assertEquals(bridge.canvasRows.size, all.lines().size, "every row should be copied on a separate line")
    assertTrue("message number 1 " in all && "message number 40" in all)
    clipboard.text = null
    scene.key(Key.Escape, meta = false, frame)
    scene.copy(frame)
    assertFalse(!clipboard.text.isNullOrEmpty(), "Escape should clear the selection")
  }
}
