package com.cactus.logcactus.standalone

import androidx.compose.runtime.snapshots.Snapshot
import com.cactus.logcactus.presentation.logcat.compose.ComposeUiBridge
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewSettingsStoreTest {
  private fun tempStore(): Pair<ViewSettingsStore, java.nio.file.Path> {
    val dir = Files.createTempDirectory("logcactus-view")
    val file = dir.resolve("view.properties")
    return ViewSettingsStore(file) to file
  }

  @Test
  fun missingFileMeansNoSettings() {
    assertNull(tempStore().first.load())
  }

  @Test
  fun roundTripKeepsEveryValue() {
    val (store, file) = tempStore()
    val s = ViewSettings(
      showTime = false, showTag = true, showAppName = false, showProcess = true,
      timeWidth = 10, tagWidth = 20, appNameWidth = 30, processWidth = 40,
      fontSize = 16, wrapLines = false, foldStacks = false, autoExpandJson = false, hideDuplicateTags = true
    )
    store.save(s)
    assertEquals(s, store.load())
    Files.deleteIfExists(file)
  }

  @Test
  fun brokenValuesFallBackToDefaultsAndNumbersAreClamped() {
    val (store, file) = tempStore()
    Files.writeString(file, "showTime=maybe\ntimeWidth=9999\nfontSize=-5\ntagWidth=abc\nwrapLines=false\n")
    val s = store.load()!!
    val d = ViewSettings()
    assertEquals(d.showTime, s.showTime)
    assertEquals(32, s.timeWidth)
    assertEquals(8, s.fontSize)
    assertEquals(d.tagWidth, s.tagWidth)
    assertFalse(s.wrapLines)
    Files.deleteIfExists(file)
  }

  @Test
  fun bridgeRestoresSavedSettingsAndSavesChanges() {
    val (store, file) = tempStore()
    store.save(ViewSettings(showTag = false, fontSize = 17, hideDuplicateTags = true))
    val first = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog(), store)
    assertFalse(first.showTag)
    assertEquals(17, first.logFontSize)
    assertTrue(first.hideDuplicateTags)

    // изменение сохраняется с паузой (debounce) — ждём запись и читаем новым мостом
    first.showProcess = false
    first.timeWidth = 20
    Snapshot.sendApplyNotifications()
    var saved: ViewSettings? = null
    repeat(60) {
      saved = store.load()
      if (saved?.showProcess == false) return@repeat
      Thread.sleep(50)
    }
    assertEquals(false, saved?.showProcess)
    val second = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog(), store)
    assertFalse(second.showProcess)
    assertEquals(20, second.timeWidth)
    first.dispose(); second.dispose()
    Files.deleteIfExists(file)
  }

  @Test
  fun bridgeWithoutStoreNeverTouchesFiles() {
    val bridge = ComposeUiBridge(SimpleDeviceProvider(), DemoDeviceCatalog())
    assertTrue(bridge.showTime && bridge.wrapLines && bridge.foldStacks) // умолчания
    bridge.dispose()
  }
}

class ViewSettingsMergeTest {
  @Test
  fun savingOneTabKeepsAnotherTabsFreshChange() {
    val dir = Files.createTempDirectory("logcactus-merge")
    val store = ViewSettingsStore(dir.resolve("view.properties"))
    val base = ViewSettings()
    store.save(base)
    store.saveChanged(base, base.copy(fontSize = 18))           // вкладка A меняет шрифт
    store.saveChanged(base, base.copy(wrapLines = false))       // вкладка B (знает только base) меняет перенос
    val result = store.load()!!
    assertEquals(18, result.fontSize)
    assertFalse(result.wrapLines)
  }
}
