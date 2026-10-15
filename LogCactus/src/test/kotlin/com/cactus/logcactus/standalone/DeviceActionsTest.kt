package com.cactus.logcactus.standalone

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceActionsTest {
  private fun actions(reply: (String) -> String?): Pair<DeviceActions, MutableList<String>> {
    val log = mutableListOf<String>()
    return DeviceActions { _, cmd -> log.add(cmd); reply(cmd) } to log
  }

  @Test
  fun clearDataReportsSuccessAndFailure() {
    assertTrue(actions { "Success" }.first.clearData("S", "com.a").ok)
    assertFalse(actions { "Failed" }.first.clearData("S", "com.a").ok)
    assertFalse(actions { null }.first.clearData("S", "com.a").ok)
  }

  @Test
  fun clearCacheUsesCacheOnlyThenFallsBackToRunAs() {
    val (a, log) = actions { cmd -> if (cmd.startsWith("getprop")) "33" else "Success" }
    assertTrue(a.clearCache("S", "com.a").ok)
    assertEquals(listOf("getprop ro.build.version.sdk", "pm clear --cache-only com.a"), log)

    val (b, log2) = actions { cmd -> if (cmd.startsWith("getprop")) "33" else if (cmd.startsWith("pm clear")) "Error: unknown option" else "done" }
    val r = b.clearCache("S", "com.a")
    assertTrue(r.ok)
    assertTrue(log2.last().startsWith("run-as com.a"), log2.toString())

    val (c, _) = actions { cmd -> if (cmd.startsWith("getprop")) "33" else if (cmd.startsWith("pm clear")) "Failed" else "run-as: package not debuggable: com.a" }
    assertFalse(c.clearCache("S", "com.a").ok)
    assertFalse(a.clearCache("S", "com.a; id").ok)
  }

  @Test
  fun restartStopsThenLaunches() {
    val (a, log) = actions { "Events injected: 1" }
    assertTrue(a.restart("S", "com.a").ok)
    assertEquals(listOf("am force-stop com.a", "monkey -p com.a -c android.intent.category.LAUNCHER 1"), log)
    assertFalse(actions { "** No activities found to run, monkey aborted." }.first.restart("S", "com.a").ok)
  }

  @Test
  fun shellInjectionIsRejected() {
    val (a, log) = actions { "Success" }
    assertFalse(a.forceStop("S", "com.a; rm -rf /").ok)
    assertFalse(a.clearData("S", "com.a && id").ok)
    assertFalse(a.openDeepLink("S", "app://x'; id; '", null).ok)
    assertFalse(a.openDeepLink("S", "app://x y", null).ok)
    assertTrue(log.isEmpty(), log.toString())
  }

  @Test
  fun deepLinkIsQuotedAndTargetsPackage() {
    val (a, log) = actions { "Starting: Intent { act=android.intent.action.VIEW }" }
    assertTrue(a.openDeepLink("S", "myapp://open?id=1&x=2", "com.a").ok)
    assertEquals("am start -a android.intent.action.VIEW -d 'myapp://open?id=1&x=2' com.a", log.single())
    assertFalse(actions { "Error: Activity not started, unable to resolve Intent" }.first.openDeepLink("S", "x://y", null).ok)
  }

  @Test
  fun parsesAppDetails() {
    val dump = """
      versionCode=150022361 minSdk=29 targetSdk=35
      versionName=2.23.61
      flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]
      firstInstallTime=2026-01-02 10:00:00
      lastUpdateTime=2026-09-01 12:30:00
      installerPackageName=com.android.vending
    """.trimIndent()
    val d = AppDetailsParser.parse("com.a", dump)!!
    assertEquals("2.23.61", d.versionName)
    assertEquals("35", d.targetSdk)
    assertTrue(d.debuggable)
    assertEquals("com.android.vending", d.installer)
    assertEquals("2026-09-01 12:30:00", d.lastUpdate)
    assertFalse(AppDetailsParser.parse("com.a", "versionName=1\nflags=[ HAS_CODE ]")!!.debuggable)
    assertNull(AppDetailsParser.parse("com.a", "nothing"))
  }
}

class AppCatalogVersionTest {
  @org.junit.jupiter.api.Test
  fun versionsCompareNumerically() {
    org.junit.jupiter.api.Assertions.assertTrue(AppCatalog.compareVersions("34.0.0", "9.0.0") > 0)
    org.junit.jupiter.api.Assertions.assertTrue(AppCatalog.compareVersions("33.0.2", "33.0.10") < 0)
  }
}
