package com.cactus.logcactus.standalone

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiAdbTest {
  private fun wifi(reply: (List<String>) -> String?): Pair<WifiAdb, MutableList<List<String>>> {
    val log = mutableListOf<List<String>>()
    return WifiAdb { args -> log.add(args); reply(args) } to log
  }

  @Test
  fun parsesIpFromIpAddrOutput() {
    val out = "23: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500\n    inet 192.168.0.20/24 brd 192.168.0.255 scope global wlan0\n"
    assertEquals("192.168.0.20", WifiAdb.parseIp(out))
    assertNull(WifiAdb.parseIp("Device \"wlan0\" does not exist."))
  }

  @Test
  fun connectResultsAreInterpreted() {
    assertTrue(WifiAdb.parseConnect("connected to 192.168.0.5:5555", "192.168.0.5:5555").ok)
    assertTrue(WifiAdb.parseConnect("already connected to 192.168.0.5:5555", "192.168.0.5:5555").ok)
    assertFalse(WifiAdb.parseConnect("failed to connect to '192.168.0.5:5555': Connection refused", "192.168.0.5:5555").ok)
  }

  @Test
  fun inputIsValidatedBeforeRunningAdb() {
    val (w, log) = wifi { "connected to x" }
    assertFalse(w.connect("not an address").ok)
    assertFalse(w.connect("1.2.3.4:5555; rm -rf /").ok)
    assertFalse(w.pair("192.168.0.5:4444", "12ab56").ok)
    assertFalse(w.pair("bad", "123456").ok)
    assertFalse(w.disconnect("USBSERIAL").ok)
    assertTrue(log.isEmpty(), log.toString())
  }

  @Test
  fun pairAndConnectPassArgumentsAsList() {
    val (w, log) = wifi { args -> if (args[0] == "pair") "Successfully paired to 192.168.0.5:44444 [guid=adb-X]" else "connected to 192.168.0.5:37000" }
    assertTrue(w.pair("192.168.0.5:44444", "123456").ok)
    assertTrue(w.connect("192.168.0.5:37000").ok)
    assertEquals(listOf(listOf("pair", "192.168.0.5:44444", "123456"), listOf("connect", "192.168.0.5:37000")), log)
    assertFalse(wifi { "Failed: Wrong password or connection was dropped" }.first.pair("192.168.0.5:44444", "000000").ok)
  }

  @Test
  fun enableFromUsbFindsIpRunsTcpipAndConnects() {
    val (w, log) = wifi { args ->
      when {
        "ip -f inet addr show wlan0" in args.last() -> "    inet 192.168.0.20/24 brd 192.168.0.255 scope global wlan0"
        args.contains("tcpip") -> "restarting in TCP mode port: 5555"
        args[0] == "connect" -> "connected to 192.168.0.20:5555"
        else -> null
      }
    }
    val (result, target) = w.enableFromUsb("c7e84296")
    assertTrue(result.ok, result.message)
    assertEquals("192.168.0.20:5555", target)
    assertEquals(listOf("-s", "c7e84296", "tcpip", "5555"), log[1])
    assertEquals(listOf("connect", "192.168.0.20:5555"), log[2])
    assertFalse(w.enableFromUsb("192.168.0.20:5555").first.ok)
    assertFalse(wifi { null }.first.enableFromUsb("S").first.ok)
  }

  @Test
  fun parsesMdnsServices() {
    val out = """
      List of discovered mdns services
      adb-ABC123-xyz	_adb-tls-connect._tcp.	192.168.0.12:37123
      adb-ABC123-xyz	_adb-tls-pairing._tcp.	192.168.0.12:44444
      printer	_ipp._tcp.	192.168.0.30:631
    """.trimIndent()
    val services = WifiAdb.parseMdns(out)
    assertEquals(2, services.size)
    assertEquals(MdnsService("adb-ABC123-xyz", "192.168.0.12:44444", true), services.first { it.pairing })
    assertFalse(services.first { !it.pairing }.pairing)
  }
}
