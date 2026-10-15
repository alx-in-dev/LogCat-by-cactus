package com.cactus.logcactus.standalone

import java.io.File
import java.time.LocalDateTime
import javax.swing.SwingUtilities

/**
 * Если Swing-поток не отвечает дольше [stallMs], дописывает дамп всех потоков в ~/.logcactus/freeze.log.
 * Нужен для багов вида «после блокировки экрана кнопки не нажимаются»: по дампу видно, на чём встал UI.
 */
object UiFreezeWatchdog {
  fun start(stallMs: Long = 5_000, file: File = File(System.getProperty("user.home"), ".logcactus/freeze.log")) {
    val thread = Thread({
      var reported = false
      while (true) {
        val answered = java.util.concurrent.atomic.AtomicBoolean(false)
        runCatching { SwingUtilities.invokeLater { answered.set(true) } }
        Thread.sleep(stallMs)
        if (answered.get()) {
          reported = false
        } else if (!reported) {
          reported = true
          runCatching { dump(file) }
        }
      }
    }, "logcactus-freeze-watchdog")
    thread.isDaemon = true
    thread.start()
  }

  private fun dump(file: File) {
    file.parentFile?.mkdirs()
    val text = buildString {
      append("=== UI не отвечает > ").append(LocalDateTime.now()).append('\n')
      for ((t, stack) in Thread.getAllStackTraces()) {
        append("\"").append(t.name).append("\" ").append(t.state).append('\n')
        stack.forEach { append("    at ").append(it).append('\n') }
      }
      append('\n')
    }
    // Не раздуваем файл при повторных зависаниях.
    if (file.exists() && file.length() > 512_000) file.writeText("")
    file.appendText(text)
  }
}
