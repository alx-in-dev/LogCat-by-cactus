package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.presentation.logcat.CanvasItem
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Canvas-список (прототип): подсветка поиска, hideDuplicateTags, ползунок скроллбара, навигация по кадрам стека. */
@OptIn(ExperimentalComposeUiApi::class)
class CanvasFinishTest {
  private fun withScene(list: List<LogEntry>, block: (ImageComposeScene, () -> Unit, ComposeUiBridge) -> Unit) {
    System.setProperty("logcactus.canvas", "true")
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      bridge.loadSession(SessionFile.Session(emptyMap(), list))
      repeat(60) { if (bridge.canvasItems.isEmpty()) Thread.sleep(50) }
      javax.swing.SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(1000, 600, Density(1f)) { LogcatComposeTheme { LogcatComposeScreen(bridge) } }
        var t = 0L
        val frame = { t += 16_000_000; scene.render(t); Unit }
        repeat(20) { frame() }
        block(scene, frame, bridge)
        scene.close()
      }
      assertTrue(errors.isEmpty(), errors.joinToString { it.toString() })
    } finally {
      System.setProperty("logcactus.canvas", "false")
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  private fun shot(scene: ImageComposeScene) = scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes

  private fun simple(n: Int = 40) = (1..n).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "message number $it alpha beta") }

  @Test
  fun searchMatchesAreFoundAndHighlighted() = withScene(simple()) { scene, frame, bridge ->
    val matches = bridge.canvasSearchMatches(Regex("alpha"), bridge.canvasItems)
    assertEquals(40, matches.size)
    assertEquals(0, matches.first().rowIndex)
    val before = shot(scene)
    bridge.canvasSearchHighlight = ComposeUiBridge.CanvasHighlight(matches.groupBy { it.rowIndex }, matches.last())
    repeat(3) { frame() }
    val after = shot(scene)
    File("build/canvas-search.png").writeBytes(after)
    assertFalse(before.contentEquals(after), "search highlight should change the picture")
  }

  @Test
  fun searchJumpScrollsToTheMatchRow() = withScene((1..400).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", if (it == 5) "needle here" else "line $it") }) { scene, frame, bridge ->
    val match = bridge.canvasSearchMatches(Regex("needle"), bridge.canvasItems).single()
    bridge.scrollCanvasToItem(match.rowIndex)
    repeat(10) { frame() }
    assertFalse(bridge.follow)
    assertTrue(bridge.canvasTopIndex in 3..6, "match row should be at the top, top index was ${bridge.canvasTopIndex}")
  }

  @Test
  fun hideDuplicateTagsBlanksTheRepeatedTag() = withScene(simple(3)) { _, _, bridge ->
    val item = bridge.canvasItems[1]
    assertTrue("Tag" in bridge.canvasItemText(item, "Tag").text)
    bridge.hideDuplicateTags = true
    val hidden = bridge.canvasItemText(item, "Tag").text
    assertFalse("Tag" in hidden, hidden)
    assertEquals(bridge.canvasItemText(item, null).text.length, hidden.length, "tag is replaced by spaces, columns stay aligned")
  }

  @Test
  fun draggingTheScrollbarThumbScrollsTheList() = withScene((1..600).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "line $it") }) { scene, frame, bridge ->
    val before = shot(scene)
    val mask = androidx.compose.ui.input.pointer.PointerButtons(isPrimaryPressed = true)
    // ползунок у нижнего края правой кромки списка (следим за концом)
    scene.sendPointerEvent(PointerEventType.Move, Offset(986f, 578f)); frame()
    scene.sendPointerEvent(PointerEventType.Press, Offset(986f, 578f), buttons = mask, button = PointerButton.Primary); frame()
    for (y in listOf(500f, 400f, 300f, 200f, 120f)) { scene.sendPointerEvent(PointerEventType.Move, Offset(986f, y), buttons = mask); frame() }
    scene.sendPointerEvent(PointerEventType.Release, Offset(986f, 120f), button = PointerButton.Primary); frame()
    repeat(5) { frame() }
    File("build/canvas-scrollbar.png").writeBytes(shot(scene))
    assertFalse(bridge.follow, "dragging the thumb up should stop following the tail")
    assertFalse(before.contentEquals(shot(scene)))
  }

  @Test
  fun stackFrameNavigationJumpsToTheFoldedPlaceholder() {
    fun e(m: String) = LogEntry(1000L, "ERROR", "AndroidRuntime", 7, "p", "com.a", m)
    val list = (1..200).map { e("normal $it") } + e("boom") + (1..6).map { e("\tat com.a.M.m$it(M.java:$it)") } + (1..30).map { e("tail $it") }
    withScene(list) { _, frame, bridge ->
      bridge.jumpToStackFrameCanvas(forward = true)
      repeat(10) { frame() }
      val stack = bridge.canvasItems.indexOfFirst { it.kind == CanvasItem.Kind.STACK }
      assertTrue(stack >= 0)
      assertFalse(bridge.follow)
      assertTrue(bridge.canvasTopIndex in (stack - 1)..(stack + 1), "top ${bridge.canvasTopIndex} vs stack $stack")
    }
  }
}
