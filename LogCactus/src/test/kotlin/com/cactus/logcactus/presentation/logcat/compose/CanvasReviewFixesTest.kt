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
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogcatUiState
import com.cactus.logcactus.presentation.logcat.CanvasItem
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессии по код-ревью (блок A): якорь вида, ленивое форматирование, копирование дерева JSON, F2.
 * Сцена живёт в EDT, но шаги тест делает с потока теста короткими вызовами [edt]: паузы между ними нужны, чтобы мост
 * (Dispatchers.Swing) успевал применять добавленные строки — внутри одного invokeAndWait EDT был бы занят.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CanvasReviewFixesTest {
  private class FakeClipboard : ClipboardManager {
    var text: String? = null
    override fun getText(): AnnotatedString? = text?.let { AnnotatedString(it) }
    override fun setText(annotatedString: AnnotatedString) { text = annotatedString.text }
  }

  private class Ui(val scene: ImageComposeScene) {
    private var t = 0L
    fun <T> edt(f: () -> T): T { var r: Any? = null; SwingUtilities.invokeAndWait { r = f() }; @Suppress("UNCHECKED_CAST") return r as T }
    fun frame() = edt { t += 16_000_000; scene.render(t); Unit }
    fun frames(n: Int, pauseMs: Long = 0) = repeat(n) { if (pauseMs > 0) Thread.sleep(pauseMs); frame() }
    fun move(x: Float, y: Float, mask: PointerButtons? = null) { edt { scene.sendPointerEvent(PointerEventType.Move, Offset(x, y), buttons = mask) }; frame() }
    fun press(x: Float, y: Float) { edt { scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary) }; frame() }
    fun release(x: Float, y: Float) { edt { scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), button = PointerButton.Primary) }; frame() }
    fun copy() { edt { scene.sendKeyEvent(KeyEvent(Key.C, KeyEventType.KeyDown, isMetaPressed = true)) }; frame() }
    fun wheelUp(notches: Int) {
      move(400f, 300f)
      repeat(notches) { edt { scene.sendPointerEvent(PointerEventType.Scroll, Offset(400f, 300f), scrollDelta = Offset(0f, -8f)) }; frames(4) }
    }
    /** Кадры, пока индекс верхней строки не перестанет меняться (у колеса прокрутки есть инерция/анимация). */
    fun settle(bridge: ComposeUiBridge) {
      var same = 0; var last = -2
      repeat(200) { frame(); Thread.sleep(5); if (bridge.canvasTopIndex == last) same++ else { same = 0; last = bridge.canvasTopIndex }; if (same >= 8) return }
    }
  }

  private fun line(i: Int, msg: String = "line $i") = LogEntry(1000L + i, "INFO", "Tag", 7, "p", "com.a", msg)

  private fun withScene(list: List<LogEntry>, block: (Ui, ComposeUiBridge, FakeClipboard) -> Unit) {
    System.setProperty("logcactus.canvas", "true")
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    var scene: ImageComposeScene? = null
    try {
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      bridge.loadSession(SessionFile.Session(emptyMap(), list))
      repeat(80) { if (bridge.canvasRows.size < list.size) Thread.sleep(50) }
      val clipboard = FakeClipboard()
      SwingUtilities.invokeAndWait {
        scene = ImageComposeScene(1000, 600, Density(1f)) {
          CompositionLocalProvider(LocalClipboardManager provides clipboard) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        }
      }
      val ui = Ui(scene!!)
      ui.frames(20)
      block(ui, bridge, clipboard)
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      scene?.let { s -> SwingUtilities.invokeAndWait { s.close() } }
      System.setProperty("logcactus.canvas", "false")
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  private fun append(bridge: ComposeUiBridge, all: List<LogEntry>) {
    bridge.render(LogcatUiState(null, null, "VERBOSE", "", "", true, false, all))
  }

  @Test
  fun viewStaysPutWhenRowsArriveAfterAFollowPeriod() {
    val base = (1..300).map { line(it) }
    withScene(base) { ui, bridge, _ ->
      // слежение включено, строки приходят — якорь обновляется каждый кадр
      var all = base
      repeat(3) { r ->
        all = all + (1..40).map { line(1000 + r * 100 + it) }
        append(bridge, all)
        ui.frames(20, pauseMs = 15)
      }
      ui.wheelUp(notches = 3)
      ui.settle(bridge)
      assertFalse(bridge.follow, "scrolling up stops following")
      val top = bridge.canvasTopIndex
      // новые строки снизу не должны сдвигать видимое содержимое
      all = all + (1..30).map { line(5000 + it) }
      append(bridge, all)
      ui.frames(25, pauseMs = 15)
      ui.settle(bridge)
      assertTrue(bridge.canvasRows.size == all.size, "the bridge must have applied the appended rows")
      assertEquals(top, bridge.canvasTopIndex, "top row index must not change when rows are appended below")
    }
  }

  @Test
  fun farRowsBelowTheScreenAreNotFormatted() {
    val list = (1..6000).map { line(it) }
    withScene(list) { ui, bridge, _ ->
      ui.wheelUp(notches = 60) // далеко вверх: ниже экрана тысячи строк
      ui.settle(bridge)
      assertFalse(bridge.follow)
      val before = bridge.canvasTextBuilds
      ui.frames(20)
      val perFrame = (bridge.canvasTextBuilds - before) / 20.0
      assertTrue(perFrame < 200, "text must not be rebuilt for rows far below the screen, builds/frame = $perFrame")
    }
  }

  @Test
  fun selectionInsideTheJsonTreeIsCopied() {
    val json = line(2, """Body: {"user":{"id":42,"name":"Ann"},"tags":["a","b"],"ok":true}""")
    withScene(listOf(line(1, "Response:"), json)) { ui, bridge, clipboard ->
      assertTrue(bridge.canvasItems.count { it.kind == CanvasItem.Kind.DETAIL } > 3)
      val mask = PointerButtons(isPrimaryPressed = true)
      // тянем по нескольким нижним строкам дерева (DETAIL): раньше результат был пустым
      ui.move(300f, 560f)
      ui.press(300f, 560f)
      ui.move(500f, 590f, mask)
      ui.move(700f, 592f, mask)
      ui.release(700f, 592f)
      ui.copy()
      assertFalse(clipboard.text.isNullOrEmpty(), "selection inside the expanded JSON tree must be copyable")
    }
  }

  @Test
  fun f2MovesThroughBookmarksWithoutSticking() {
    val list = (1..400).map { line(it) }
    withScene(list) { ui, bridge, _ ->
      val rows = bridge.canvasRows
      bridge.toggleBookmarkEntry(rows[10], "line 11")
      bridge.toggleBookmarkEntry(rows[20], "line 21")
      bridge.jumpToBookmarkCanvas(forward = true)
      ui.frames(10, pauseMs = 10); ui.settle(bridge)
      assertEquals(10, bridge.canvasTopIndex)
      bridge.jumpToBookmarkCanvas(forward = true)
      ui.frames(10, pauseMs = 10); ui.settle(bridge)
      assertEquals(20, bridge.canvasTopIndex, "second F2 must move to the next bookmark, not stick on the first")
      bridge.jumpToBookmarkCanvas(forward = false)
      ui.frames(10, pauseMs = 10); ui.settle(bridge)
      assertEquals(10, bridge.canvasTopIndex)
    }
  }

  @Test
  fun bugReportSourcesAreVisibleLinesAndSelection() {
    val list = (1..200).map { line(it) }
    withScene(list) { ui, bridge, _ ->
      ui.frames(5)
      val visible = bridge.canvasVisibleLines()
      assertTrue(visible.isNotEmpty() && visible.size < 200, "visible lines only: ${visible.size}")
      assertTrue(bridge.canvasSelectionText().isEmpty())
      ui.press(300f, 560f); ui.move(500f, 580f, PointerButtons(isPrimaryPressed = true)); ui.release(500f, 580f)
      assertFalse(bridge.canvasSelectionText().isEmpty(), "selection must be exposed to the bug report")
    }
  }
}
