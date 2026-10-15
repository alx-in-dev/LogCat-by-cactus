package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
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
import com.cactus.logcactus.presentation.logcat.CanvasItem
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Canvas-список (прототип): свёртка стектрейса по клику, раскрытый JSON, копирование разворачивает заглушки. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CanvasFoldingTest {
  private class FakeClipboard : ClipboardManager {
    var text: String? = null
    override fun getText(): AnnotatedString? = text?.let { AnnotatedString(it) }
    override fun setText(annotatedString: AnnotatedString) { text = annotatedString.text }
  }

  private fun entries(): List<LogEntry> {
    fun e(m: String) = LogEntry(1000L, "ERROR", "AndroidRuntime", 7, "p", "com.a", m)
    return (1..5).map { e("normal $it") } + e("java.lang.IllegalStateException: boom") +
      (1..6).map { e("\tat com.a.Main.m$it(Main.java:$it)") } + e("Caused by: x") + e("tail 1") + e("tail 2")
  }

  private fun withScene(list: List<LogEntry>, block: (ImageComposeScene, () -> Unit, ComposeUiBridge, FakeClipboard) -> Unit) {
    System.setProperty("logcactus.canvas", "true")
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      bridge.loadSession(SessionFile.Session(emptyMap(), list))
      repeat(60) { if (bridge.canvasItems.isEmpty()) Thread.sleep(50) }
      val clipboard = FakeClipboard()
      javax.swing.SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(1000, 600, Density(1f)) {
          CompositionLocalProvider(LocalClipboardManager provides clipboard) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        }
        var t = 0L
        val frame = { t += 16_000_000; scene.render(t); Unit }
        repeat(20) { frame() }
        block(scene, frame, bridge, clipboard)
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      System.setProperty("logcactus.canvas", "false")
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  private fun ImageComposeScene.click(x: Float, y: Float, frame: () -> Unit) {
    sendPointerEvent(PointerEventType.Move, Offset(x, y)); frame()
    sendPointerEvent(PointerEventType.Press, Offset(x, y), button = PointerButton.Primary); frame()
    sendPointerEvent(PointerEventType.Release, Offset(x, y), button = PointerButton.Primary); frame()
  }

  private fun ImageComposeScene.key(key: Key, frame: () -> Unit) {
    sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isMetaPressed = true)); frame()
  }

  @Test
  fun stackIsFoldedClickExpandsAndSelectAllCopiesEveryOriginalLine() = withScene(entries()) { scene, frame, bridge, clipboard ->
    val all = entries().size
    val stack = bridge.canvasItems.single { it.kind == CanvasItem.Kind.STACK }
    assertEquals(6, stack.hidden.size)
    assertEquals(all - 6 + 1, bridge.canvasItems.size)
    File("build/canvas-folded.png").writeBytes(scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)

    // копирование: заглушка разворачивается в свои кадры — в буфере ровно столько строк, сколько было записей
    scene.click(300f, 300f, frame)
    scene.key(Key.A, frame)
    scene.key(Key.C, frame)
    assertEquals(all, clipboard.text.orEmpty().lines().size, clipboard.text)
    assertTrue("\tat com.a.Main.m3(Main.java:3)" in clipboard.text.orEmpty() || "Main.m3" in clipboard.text.orEmpty())

    // клик по заглушке раскрывает серию: +6 кадров
    val before = bridge.canvasItems.size
    // заглушка — третья снизу (после неё "Caused by" и два хвоста… ищем по y: ниже идут 3 строки по ~18px)
    bridge.toggleCanvasExpanded(stack)
    repeat(5) { frame() }
    assertEquals(before + 6, bridge.canvasItems.size)
    bridge.toggleCanvasExpanded(bridge.canvasItems.single { it.kind == CanvasItem.Kind.STACK })
    assertEquals(before, bridge.canvasItems.size)
  }

  @Test
  fun clickOnPlaceholderExpandsIt() = withScene(entries()) { scene, frame, bridge, _ ->
    // снизу вверх: tail 2, tail 1, Caused by, заглушка — четвёртая строка от нижнего края (~18px каждая)
    val before = bridge.canvasItems.size
    scene.click(300f, 50f + 545f - 18f * 3.5f, frame)
    repeat(5) { frame() }
    assertEquals(before + 6, bridge.canvasItems.size, "clicking the placeholder should expand the frames")
    File("build/canvas-unfolded.png").writeBytes(scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
  }

  @Test
  fun jsonAfterPrefixIsShownAsTreeUnderIt() {
    val json = LogEntry(2000L, "INFO", "Net", 7, "p", "com.a", """Body: {"user":{"id":42,"name":"Ann"},"tags":["a","b"],"ok":true}""")
    withScene(listOf(LogEntry(1999L, "INFO", "Net", 7, "p", "com.a", "Response:"), json)) { scene, frame, bridge, clipboard ->
      val kinds = bridge.canvasItems.map { it.kind }
      assertEquals(CanvasItem.Kind.ENTRY, kinds.first())
      assertEquals(CanvasItem.Kind.HEAD, kinds[1])
      assertTrue(kinds.drop(2).all { it == CanvasItem.Kind.DETAIL } && kinds.size > 5)
      File("build/canvas-json.png").writeBytes(scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
      scene.click(300f, 300f, frame)
      scene.key(Key.A, frame)
      scene.key(Key.C, frame)
      // JSON-дети не дублируются в копировании: две исходные строки
      assertEquals(2, clipboard.text.orEmpty().lines().size, clipboard.text)
      assertTrue("Body: {\"user\"" in clipboard.text.orEmpty())
    }
  }
}
