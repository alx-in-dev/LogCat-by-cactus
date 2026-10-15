package com.cactus.logcactus.presentation.logcat

import com.cactus.logcactus.standalone.AppVersion
import com.cactus.logcactus.standalone.DeviceInfo
import com.cactus.logcactus.standalone.DeviceInfoCollector
import com.cactus.logcactus.standalone.DeviceInfoParser
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BugReportTest {
  private val now = ZonedDateTime.of(2026, 9, 30, 17, 40, 12, 0, ZoneOffset.ofHours(3))
  private val device = DeviceInfo("OnePlus", "CPH2573", "16", "36", "OnePlus/CPH2573IN/OP595DL1:16/user")

  @Test
  fun headerContainsDeviceAppDateAndFilter() {
    val md = BugReport.build(now, device, AppVersion("com.a", "1.2.3", "456"), "package:mine", "WARN", listOf("l1", "l2"), "bookmarked")
    assertTrue("**Date:** 2026-09-30 17:40:12 +03:00" in md, md)
    assertTrue("**Device:** OnePlus CPH2573" in md, md)
    assertTrue("**Android:** 16 (API 36)" in md, md)
    assertTrue("**App:** com.a 1.2.3 (456)" in md, md)
    assertTrue("**Filter:** `package:mine`, level ≥ WARN" in md, md)
    assertTrue("**Lines:** 2 (bookmarked)" in md, md)
    assertTrue("```log\nl1\nl2\n```" in md, md)
  }

  @Test
  fun optionalPartsAreOmitted() {
    val md = BugReport.build(now, null, null, "", "VERBOSE", listOf("x"), "last visible")
    assertTrue("**Device:**" !in md)
    assertTrue("**App:**" !in md)
    assertTrue("**Filter:**" !in md)
  }

  @Test
  fun fenceIsLongerThanBackticksInsideLog() {
    val md = BugReport.build(now, null, null, "", "VERBOSE", listOf("code ``` here"), "x")
    assertTrue("````log\ncode ``` here\n````" in md, md)
  }

  @Test
  fun parsesDevicePropsAndAppVersion() {
    val info = DeviceInfoParser.parseProps("OnePlus\nCPH2573\n16\n36\nfp\n")
    assertEquals("OnePlus CPH2573", info.title)
    assertEquals("36", info.sdk)
    val v = DeviceInfoParser.parseVersion(
      "com.a",
      "    versionCode=150022361 minSdk=29 targetSdk=35\n    versionName=2.23.61_c58\nFailed to write while dumping service package: Broken pipe"
    )
    assertEquals(AppVersion("com.a", "2.23.61_c58", "150022361"), v)
    assertNull(DeviceInfoParser.parseVersion("com.a", "nothing"))
  }

  @Test
  fun collectorRejectsSuspiciousPackage() {
    val c = DeviceInfoCollector { _, _ -> error("must not run") }
    assertNull(c.app("S", "com.a; rm -rf /"))
  }
}
