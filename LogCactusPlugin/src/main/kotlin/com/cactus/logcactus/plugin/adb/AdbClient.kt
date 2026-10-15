package com.cactus.logcactus.plugin.adb

import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.logcat.standalone.core.AdbLongLogcatParser
import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class AdbDevice(val serial: String, val state: String, val title: String) {
  val online: Boolean get() = state == "device"
}

/** Тонкая обёртка над adb Студии (`AdbFileProvider`): список устройств, поток logcat, очистка. Своих потоков и хуков нет. */
class AdbClient(private val project: Project) {
  private val log = logger<AdbClient>()

  private fun adbFile(): File? = AdbFileProvider.fromProject(project)?.get()?.takeIf { it.exists() }

  suspend fun devices(): List<AdbDevice> = withContext(Dispatchers.IO) {
    val adb = adbFile() ?: return@withContext emptyList()
    val out = runOnce(adb, listOf("devices", "-l")) ?: return@withContext emptyList()
    parseDevices(out)
  }

  suspend fun clear(serial: String) = withContext(Dispatchers.IO) {
    adbFile()?.let { runOnce(it, listOf("-s", serial, "logcat", "-c")) }
    Unit
  }

  /** Поток записей; процесс adb убивается при отмене коллектора. */
  fun logcat(serial: String): Flow<LogEntry> = flow {
    val adb = adbFile() ?: error("adb not found")
    val process = ProcessBuilder(adb.path, "-s", serial, "logcat", "-v", "long").redirectErrorStream(true).start()
    val resolver = ProcessNames(adb, serial)
    try {
      val parser = AdbLongLogcatParser()
      process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        while (true) {
          currentCoroutineContext().ensureActive()
          val line = reader.readLine() ?: break
          for (entry in parser.acceptLine(line)) emit(resolver.enrich(entry))
        }
      }
    } finally {
      process.destroy()
      if (process.isAlive) process.destroyForcibly()
    }
  }.flowOn(Dispatchers.IO)

  private fun runOnce(adb: File, args: List<String>): String? = runCatching {
    val p = ProcessBuilder(listOf(adb.path) + args).redirectErrorStream(true).start()
    if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
    p.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
  }.onFailure { log.warn("adb ${args.joinToString(" ")} failed", it) }.getOrNull()

  internal companion object {
    // "emulator-5554          device product:sdk_gphone model:sdk_gphone64_arm64 device:emu64a transport_id:1"
    private val MODEL = Regex("""model:(\S+)""")

    fun parseDevices(output: String): List<AdbDevice> = output.lineSequence()
      .drop(1)
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("*") }
      .mapNotNull { line ->
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return@mapNotNull null
        val model = MODEL.find(line)?.groupValues?.get(1)?.replace('_', ' ')
        AdbDevice(parts[0], parts[1], if (model != null) "$model (${parts[0]})" else parts[0])
      }.toList()
  }
}
