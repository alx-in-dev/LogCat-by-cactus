package com.cactus.logcactus.standalone

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForegroundAppTest {
  @Test
  fun parsesTopResumedActivityFromAndroid10Plus() {
    val dump = "  topResumedActivity=ActivityRecord{1a2b3c u0 com.example.app/.MainActivity t42}\n" +
      "  ResumedActivity: ActivityRecord{1a2b3c u0 com.example.app/.MainActivity t42}"
    assertEquals("com.example.app", ForegroundAppParser.parseResumed(dump))
  }

  @Test
  fun parsesLegacyResumedActivityWithFullClassName() {
    val dump = "  mResumedActivity: ActivityRecord{4f5e u0 org.foo.bar/org.foo.bar.ui.Home\$Inner t7}"
    assertEquals("org.foo.bar", ForegroundAppParser.parseResumed(dump))
  }

  @Test
  fun returnsNullWhenNothingResumed() {
    assertNull(ForegroundAppParser.parseResumed("  topResumedActivity=null"))
    assertNull(ForegroundAppParser.parseResumed(""))
  }

  @Test
  fun parsesWindowFocusFallback() {
    val dump = "  mCurrentFocus=Window{9ab u0 com.example.app/com.example.app.MainActivity}"
    assertEquals("com.example.app", ForegroundAppParser.parseFocus(dump))
  }

  @Test
  fun systemShellIsRecognized() {
    assertTrue(ForegroundAppParser.isSystemShell("com.android.systemui"))
    assertTrue(ForegroundAppParser.isSystemShell("com.google.android.apps.nexuslauncher"))
    assertTrue(ForegroundAppParser.isSystemShell("com.miui.home.launcher"))
    assertFalse(ForegroundAppParser.isSystemShell("com.example.app"))
  }

  @Test
  fun trackerFallsBackToWindowFocusWhenActivityDumpIsEmpty() {
    val tracker = ForegroundAppTracker { _, command ->
      if ("activity activities" in command) "" else "mCurrentFocus=Window{1 u0 com.pkg/com.pkg.Main}"
    }
    assertEquals("com.pkg", tracker.current("SERIAL"))
  }

  @Test
  fun trackerReturnsNullWhenAdbFails() {
    assertNull(ForegroundAppTracker { _, _ -> null }.current("SERIAL"))
  }
}
