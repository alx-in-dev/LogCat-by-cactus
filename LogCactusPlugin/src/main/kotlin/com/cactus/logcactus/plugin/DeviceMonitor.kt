package com.cactus.logcactus.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cactus.logcactus.plugin.adb.AdbClient
import com.cactus.logcactus.plugin.adb.AdbDevice
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Один опрос `adb devices` на проект (раз в 3 с) — вкладки читают общий список, а не дёргают adb каждая. */
@Service(Service.Level.PROJECT)
class DeviceMonitor(project: Project, private val scope: CoroutineScope) {
  private val adb = AdbClient(project)
  private var started = false

  var devices by mutableStateOf<List<AdbDevice>>(emptyList()); private set

  fun ensureStarted() {
    if (started) return
    started = true
    scope.launch {
      while (true) {
        devices = adb.devices()
        delay(POLL_MS)
      }
    }
  }

  companion object {
    private const val POLL_MS = 3000L
    fun getInstance(project: Project): DeviceMonitor = project.service()
  }
}
