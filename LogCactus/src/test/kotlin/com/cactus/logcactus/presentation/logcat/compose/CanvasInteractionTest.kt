package com.cactus.logcactus.presentation.logcat.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.SessionFile
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Canvas-список (прототип): закладки кликом по бейджу и F2, клик по ссылке, контекстное меню. */
@OptIn(ExperimentalComposeUiApi::class)
class CanvasInteractionTest {
  private fun withScene(entries: List<LogEntry>, setup: (ComposeUiBridge) -> Unit = {}, block: (ImageComposeScene, () -> Unit, ComposeUiBridge) -> Unit) {
    System.setProperty("logcactus.canvas", "true")
    val errors = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> errors.add(e) }
    try {
      val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
      setup(bridge)
      bridge.loadSession(SessionFile.Session(emptyMap(), entries))
      repeat(60) { if (bridge.canvasRows.size < entries.size) Thread.sleep(50) }
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

  private fun ImageComposeScene.click(x: Float, y: Float, frame: () -> Unit, button: PointerButton = PointerButton.Primary) {
    sendPointerEvent(PointerEventType.Move, Offset(x, y)); frame()
    // Без явной маски сцена не считает вторичную кнопку нажатой и Press не доходит до обработчиков.
    val mask = androidx.compose.ui.input.pointer.PointerButtons(isPrimaryPressed = button == PointerButton.Primary, isSecondaryPressed = button == PointerButton.Secondary)
    sendPointerEvent(PointerEventType.Press, Offset(x, y), buttons = mask, button = button); frame()
    sendPointerEvent(PointerEventType.Release, Offset(x, y), buttons = androidx.compose.ui.input.pointer.PointerButtons(), button = button); frame()
  }

  private fun simple(n: Int = 40) =
    (1..n).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "message number $it alpha") }

  @Test
  fun badgeClickTogglesBookmark() = withScene(simple()) { scene, frame, bridge ->
    // последняя строка у нижнего края; клик по полосе бейджа (локально x < 27; список начинается правее боковой панели)
    scene.click(62f, 585f, frame)
    assertEquals(1, bridge.bookmarks.size)
    assertSame(bridge.canvasRows.last(), bridge.bookmarks.first().entry)
    File("build/canvas-bookmark.png").writeBytes(scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    scene.click(62f, 585f, frame)
    assertEquals(0, bridge.bookmarks.size)
  }

  @Test
  fun f2StyleJumpScrollsToBookmarkAndStopsFollowing() = withScene((1..400).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "line $it") }) { scene, frame, bridge ->
    val target = bridge.canvasRows[4]
    bridge.toggleBookmarkEntry(target, "line 5")
    assertTrue(bridge.follow)
    bridge.jumpToBookmarkCanvas(forward = true)
    repeat(10) { frame() }
    assertNull(bridge.canvasScrollRequest, "scroll request should be consumed")
    assertFalse(bridge.follow, "jumping to a bookmark should stop following the tail")
    assertTrue(bridge.canvasTopIndex in 3..5, "target should be at the top, top index was ${bridge.canvasTopIndex}")
  }

  @Test
  fun clickOnLinkOpensItButDragDoesNot() {
    val opened = CopyOnWriteArrayList<String>()
    val entries = (1..10).map { LogEntry(1000L + it, "INFO", "Tag", 7, "p", "com.a", "https://example.com/path$it and more text") }
    withScene(entries, setup = { b ->
      b.showTime = false; b.showTag = false; b.showAppName = false; b.showProcess = false
      b.linkOpener = { opened.add(it) }
    }) { scene, frame, _ ->
      scene.click(80f, 585f, frame) // по тексту ссылки в последней строке
      assertEquals(listOf("https://example.com/path10"), opened.toList())
      // перетаскивание через ссылку — выделение, а не открытие
      scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 585f)); frame()
      scene.sendPointerEvent(PointerEventType.Press, Offset(80f, 585f), button = PointerButton.Primary); frame()
      scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 585f)); frame()
      scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 585f), button = PointerButton.Primary); frame()
      assertEquals(1, opened.size)
    }
  }

  @Test
  fun rightClickOpensContextMenu() = withScene(simple()) { scene, frame, _ ->
    scene.click(300f, 585f, frame, PointerButton.Secondary)
    repeat(20) { frame() }
    File("build/canvas-context-menu.png").writeBytes(scene.render(1L shl 40).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
  }
}
