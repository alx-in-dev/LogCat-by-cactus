package com.cactus.logcactus.standalone

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Разовые вызовы adb с таймаутом; null — adb недоступен или завис. */
object AdbShell {
  private const val TIMEOUT_SEC = 4L

  /** `adb -s <serial> shell <command>`. */
  fun run(serial: String, command: String): String? = exec(listOf("-s", serial, "shell", command))

  /**
   * `adb <args>` с жёстким таймаутом: после сна/блокировки экрана adb нередко зависает, а вызовы идут и с Swing-потока
   * (старт, список устройств) — вечное ожидание замораживало окно целиком.
   */
  fun exec(args: List<String>, timeoutSec: Long = TIMEOUT_SEC, adb: String = AdbLocator.path()): String? = runCatching {
    val process = ProcessBuilder(listOf(adb) + args)
      .redirectErrorStream(true)
      .start()
    // Ждём завершения до чтения: зависший adb не должен блокировать вызывающий поток.
    // Вывод у наших команд маленький (devices/grep/getprop) и влезает в буфер пайпа.
    if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      null
    } else {
      BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
    }
  }.getOrNull()
}
