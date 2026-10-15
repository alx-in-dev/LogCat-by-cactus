package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.DeviceRef
import java.util.concurrent.ConcurrentHashMap

class AdbDeviceCatalog(
  private val adb: () -> String = { AdbLocator.path() },
  private val timeoutSec: Long = 4L
) : DeviceCatalog {
  // Версия Android не меняется, пока устройство подключено: спрашивать её на каждом опросе незачем (и каждый раз рискуем зависнуть).
  private val propsCache = ConcurrentHashMap<String, Pair<String, String>>()

  override fun listDevices(): List<DeviceRef> {
    val out = AdbShell.exec(listOf("devices", "-l"), timeoutSec, adb()) ?: return emptyList()
    return out.lineSequence().mapNotNull(::parse).toList()
  }

  private fun parse(line: String): DeviceRef? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("List of devices attached") || trimmed.startsWith("*")) return null

    val parts = trimmed.split("\\s+".toRegex())
    if (parts.size < 2 || parts[1] != "device") return null

    val serial = parts[0]
    val model = findMetadata(parts, "model:")
    // Реальный формат — DeviceComboBoxRenderer.renderDevice(): "Name (SERIAL) Android X, API Y".
    // В скобках именно серийный номер, а не device-коднейм (device:) — на референс-скрине
    // "OnePlus CPH2573 (c7e84296)" это как раз serial из `adb devices -l`.
    val displayName = if (model.isNotEmpty()) "$model ($serial)" else serial
    val isEmulator = serial.startsWith("emulator-")
    val (androidVersion, apiLevel) = props(serial)

    return DeviceRef(serial, displayName, androidVersion, apiLevel, isEmulator)
  }

  private fun props(serial: String): Pair<String, String> {
    propsCache[serial]?.let { return it }
    val out = AdbShell.exec(
      listOf("-s", serial, "shell", "getprop ro.build.version.release; getprop ro.build.version.sdk"),
      timeoutSec,
      adb()
    ) ?: return "" to ""
    val lines = out.lines().map { it.trim() }
    val result = (lines.getOrNull(0).orEmpty()) to (lines.getOrNull(1).orEmpty())
    // Неудачный ответ не кэшируем — при следующем опросе попробуем снова.
    if (result.first.isNotEmpty() && result.second.isNotEmpty()) propsCache[serial] = result
    return result
  }

  private fun findMetadata(parts: List<String>, key: String): String {
    for (part in parts) {
      if (part.startsWith(key) && part.length > key.length) {
        return part.substring(key.length)
      }
    }
    return ""
  }
}
