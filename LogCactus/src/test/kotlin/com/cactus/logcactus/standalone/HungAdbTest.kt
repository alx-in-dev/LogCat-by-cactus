package com.cactus.logcactus.standalone

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class HungAdbTest {
  private fun fakeAdb(body: String): String {
    assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
    val f = Files.createTempFile("fake-adb", ".sh")
    f.writeText("#!/bin/sh\n$body\n")
    f.toFile().setExecutable(true)
    f.toFile().deleteOnExit()
    return f.toString()
  }

  @Test
  fun hungAdbDevicesDoesNotBlockForever() {
    val adb = fakeAdb("sleep 60")
    val started = System.nanoTime()
    val devices = AdbDeviceCatalog({ adb }, timeoutSec = 1).listDevices()
    val seconds = (System.nanoTime() - started) / 1e9
    assertTrue(devices.isEmpty())
    assertTrue(seconds < 5, "listDevices завис на $seconds с")
  }

  @Test
  fun hungGetpropDoesNotBlockForeverAndDeviceIsStillListed() {
    val adb = fakeAdb(
      """case "${'$'}*" in
  devices*) echo "List of devices attached"; echo "ABC123  device usb:1 model:Pixel transport_id:1";;
  *) sleep 60;;
esac"""
    )
    val started = System.nanoTime()
    val devices = AdbDeviceCatalog({ adb }, timeoutSec = 1).listDevices()
    val seconds = (System.nanoTime() - started) / 1e9
    assertEquals(listOf("ABC123"), devices.map { it.id })
    assertTrue(seconds < 6, "listDevices завис на $seconds с")
  }

  @Test
  fun hungLogcatClearIsBoundedByTimeout() {
    val adb = fakeAdb("sleep 60")
    val started = System.nanoTime()
    AdbShell.exec(listOf("-s", "X", "logcat", "-c"), timeoutSec = 1, adb = adb)
    assertTrue((System.nanoTime() - started) / 1e9 < 5)
  }
}
