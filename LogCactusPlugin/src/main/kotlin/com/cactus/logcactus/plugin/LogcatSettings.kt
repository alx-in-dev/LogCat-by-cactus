package com.cactus.logcactus.plugin

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Сохраняемые настройки вида и фильтра (на проект, в workspace.xml): переживают перезапуск Студии. */
@Service(Service.Level.PROJECT)
@State(name = "LogCactusSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class LogcatSettings : SimplePersistentStateComponent<LogcatSettings.SettingsState>(SettingsState()) {
  /** Сохраняемая вкладка: её фильтр и устройство (буфер логов не сохраняется). */
  class TabState : BaseState() {
    var id by string("")
    var query by string("")
    var serial by string("")
    /** Имя вкладки, если её переименовали (Rename Tab); пусто — стандартное «Logcat (N)». */
    var name by string("")
  }

  class SettingsState : BaseState() {
    var tabs by list<TabState>()

    /** Вложенное изменение вкладки платформа сама не заметит — сообщаем, что состояние изменилось. */
    fun touch() = incrementModificationCount()

    var query by string("")
    var level by string("VERBOSE")
    var softWrap by property(true)
    var foldStacks by property(true)
    var autoExpandJson by property(true)
    var autoExpandUrls by property(true)
    var hideRepeatedTags by property(true)
    var showTimestamp by property(true)
    var showPid by property(false)
    var showTag by property(true)
    var showPackage by property(true)
    var showLevel by property(true)
    /** DATE_TIME, TIME или EPOCH. */
    var timestampFormat by string("TIME")
    var tagWidth by property(23)
    var packageWidth by property(22)
    var bufferLines by property(50_000)
    var history by list<String>()
    var favorites by list<String>()
  }

  companion object {
    fun getInstance(project: Project): LogcatSettings = project.service()
    const val HISTORY_LIMIT = 30
  }
}
