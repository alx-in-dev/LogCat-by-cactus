package com.cactus.logcactus.standalone

import java.util.concurrent.TimeUnit

/** Сервис, найденный в сети через `adb mdns services`. [pairing] — сервис сопряжения (Android 11+), иначе подключения. */
data class MdnsService(val name: String, val hostPort: String, val pairing: Boolean)

/**
 * adb по Wi-Fi: быстрый режим (устройство по USB → `tcpip` + `connect`), сопряжение по коду
 * (Android 11+, «Беспроводная отладка»), подключение, отключение, поиск устройств в сети.
 * Аргументы передаются списком (без shell), адреса и код дополнительно валидируются.
 */
class WifiAdb(
  private val adb: (args: List<String>) -> String? = ::runAdb
) {
  /** IPv4-адрес устройства в Wi-Fi (`wlan0`) или null. */
  fun deviceIp(serial: String): String? {
    val out = adb(listOf("-s", serial, "shell", "ip -f inet addr show wlan0")) ?: return null
    return parseIp(out)
  }

  /** Быстрая настройка: устройство подключено по USB → включаем TCP-режим и подключаемся по его IP. */
  fun enableFromUsb(serial: String, port: Int = 5555): Pair<ActionResult, String?> {
    if (serial.contains(':')) return ActionResult(false, "The selected device is already connected over the network") to null
    val ip = deviceIp(serial) ?: return ActionResult(false, "Cannot find the device Wi-Fi address: is Wi-Fi on and connected?") to null
    val tcpip = adb(listOf("-s", serial, "tcpip", port.toString())) ?: return ActionResult(false, "adb did not respond") to null
    if (tcpip.contains("error", ignoreCase = true)) return ActionResult(false, "adb tcpip: ${tcpip.trim()}") to null
    // Демон adbd на устройстве перезапускается в TCP-режиме — даём ему секунду.
    Thread.sleep(RESTART_PAUSE_MS)
    val target = "$ip:$port"
    return connect(target) to target
  }

  fun connect(hostPort: String): ActionResult {
    if (!HOST_PORT.matches(hostPort.trim())) return ActionResult(false, "Enter the address as IP:port, for example 192.168.0.12:5555")
    val out = adb(listOf("connect", hostPort.trim())) ?: return ActionResult(false, "adb did not respond")
    return parseConnect(out, hostPort.trim())
  }

  fun pair(hostPort: String, code: String): ActionResult {
    if (!HOST_PORT.matches(hostPort.trim())) return ActionResult(false, "Enter the pairing address as IP:port from the Wireless debugging screen")
    if (!CODE.matches(code.trim())) return ActionResult(false, "The pairing code is 6 digits")
    val out = adb(listOf("pair", hostPort.trim(), code.trim())) ?: return ActionResult(false, "adb did not respond")
    return if (out.contains("Successfully paired")) {
      ActionResult(true, "Paired. Now connect using the IP:port shown on the main Wireless debugging screen")
    } else {
      ActionResult(false, "Pairing failed: " + out.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim())
    }
  }

  fun disconnect(serial: String): ActionResult {
    if (!serial.contains(':')) return ActionResult(false, "Only network devices can be disconnected")
    adb(listOf("disconnect", serial)) ?: return ActionResult(false, "adb did not respond")
    return ActionResult(true, "Disconnected $serial")
  }

  fun scan(): List<MdnsService> = adb(listOf("mdns", "services"))?.let(::parseMdns).orEmpty()

  companion object {
    private const val RESTART_PAUSE_MS = 1500L
    private const val TIMEOUT_SEC = 15L
    private val HOST_PORT = Regex("""[A-Za-z0-9.\-]+:\d{2,5}""")
    private val CODE = Regex("""\d{6}""")
    private val INET = Regex("""inet (\d{1,3}(?:\.\d{1,3}){3})/""")

    fun parseIp(output: String): String? = INET.find(output)?.groupValues?.get(1)

    fun parseConnect(output: String, hostPort: String): ActionResult {
      val text = output.trim()
      return when {
        text.startsWith("connected to") || text.startsWith("already connected") -> ActionResult(true, "Connected to $hostPort")
        else -> ActionResult(false, "Cannot connect to $hostPort: " + text.lineSequence().firstOrNull().orEmpty().ifBlank { "no answer" })
      }
    }

    // "adb-XXXX-abc\t_adb-tls-connect._tcp.\t192.168.0.12:37123"; старые версии adb печатают пробелы вместо табуляции.
    fun parseMdns(output: String): List<MdnsService> = output.lineSequence().mapNotNull { line ->
      val parts = line.trim().split(Regex("""\s+"""))
      if (parts.size < 3) return@mapNotNull null
      val type = parts[1]
      val hostPort = parts[2]
      if (!type.contains("_adb") || !HOST_PORT.matches(hostPort)) return@mapNotNull null
      MdnsService(parts[0], hostPort, pairing = type.contains("pairing"))
    }.distinct().toList()

    fun runAdb(args: List<String>): String? = runCatching {
      val process = ProcessBuilder(listOf(AdbLocator.path()) + args).redirectErrorStream(true).start()
      if (!process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching null
      }
      process.inputStream.bufferedReader().readText()
    }.getOrNull()
  }
}
