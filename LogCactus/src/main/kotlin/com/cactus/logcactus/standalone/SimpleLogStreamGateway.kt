package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.android.tools.idea.logcat.standalone.core.LogStreamGateway
import com.android.tools.idea.logcat.standalone.core.Subscription
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SimpleLogStreamGateway : LogStreamGateway {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val jobs = ConcurrentHashMap<String, Job>()
  private val counters = ConcurrentHashMap<String, AtomicInteger>()

  // -Dlogcactus.simple.rate=N — строк в секунду: воспроизводимая нагрузка для замеров производительности.
  private val intervalMs: Long = (1000.0 / java.lang.Double.parseDouble(System.getProperty("logcactus.simple.rate", "2.857"))).toLong().coerceAtLeast(1L)

  // -Dlogcactus.simple.traces=N — каждые N строк добавлять стектрейс из 12 кадров (для замеров свёртки).
  private val traceEvery: Int = Integer.getInteger("logcactus.simple.traces", 0)

  // -Dlogcactus.simple.json=N — каждые N строк добавлять запись "Body: {…}" с JSON около 3 КБ (как сетевые логи OkHttp).
  private val jsonEvery: Int = Integer.getInteger("logcactus.simple.json", 0)

  override fun subscribe(deviceId: String?, listener: java.util.function.Consumer<LogEntry>): Subscription {
    val key = deviceId ?: ""
    val index = AtomicInteger(0)
    counters[key] = index

    jobs.remove(key)?.cancel()
    val job = scope.launch {
      while (isActive) {
        val i = index.incrementAndGet()
        val level = when {
          i % 10 == 0 -> "ERROR"
          i % 5 == 0 -> "WARN"
          else -> "INFO"
        }
        val tag = TAGS[i % TAGS.size]
        val pid = 1000 + i % 7
        val msg = "Demo log line #$i from ${deviceId ?: "unknown"} " + PAYLOAD.substring(0, 20 + (i * 7) % (PAYLOAD.length - 20))
        listener.accept(LogEntry(Instant.now().toEpochMilli(), level, tag, pid, "proc$pid", "com.demo.app$pid", msg))
        if (jsonEvery > 0 && i % jsonEvery == 0) {
          val items = (1..40).joinToString(",") { k -> """{"id":${i * 100 + k},"title":"Item $k of $i","url":"https://cdn.example.com/img/$i/$k.jpeg","tags":["a","b","c"],"ok":true}""" }
          listener.accept(LogEntry(Instant.now().toEpochMilli(), "INFO", "OkHttp", pid, "proc$pid", "com.demo.app$pid", """Body: {"data":[$items],"page":$i}"""))
        }
        if (traceEvery > 0 && i % traceEvery == 0) {
          val now = Instant.now().toEpochMilli()
          listener.accept(LogEntry(now, "ERROR", "AndroidRuntime", pid, "proc$pid", "com.demo.app$pid", "java.lang.IllegalStateException: demo #$i"))
          for (k in 1..12) {
            listener.accept(LogEntry(now, "ERROR", "AndroidRuntime", pid, "proc$pid", "com.demo.app$pid", "\tat com.demo.app.Main.frame$k(Main.java:$k)"))
          }
        }
        delay(intervalMs)
      }
    }
    jobs[key] = job

    return Subscription {
      jobs.remove(key)?.cancel()
    }
  }

  override fun clear(deviceId: String?) {
    counters[deviceId ?: ""]?.set(0)
  }

  private companion object {
    val TAGS = listOf("ActivityManager", "DemoApp", "OkHttp", "AudioEffect", "system_server", "adbd", "Choreographer")
    const val PAYLOAD = "audioSchedBoost, set audiohal task boost, pid=2153, tid=6059, ret=0 command() 0xb400007492f06800, cmdCode = 65536"
  }
}
