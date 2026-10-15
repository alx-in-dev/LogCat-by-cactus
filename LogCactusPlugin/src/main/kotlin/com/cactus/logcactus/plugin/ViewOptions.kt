package com.cactus.logcactus.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Вид и история фильтров — общие для всех вкладок проекта: изменили колонки в одной вкладке — поменялись во всех.
 * Состояние для Compose, запись в [LogcatSettings] при каждом изменении.
 */
@Service(Service.Level.PROJECT)
class ViewOptions(project: Project) {
  private val settings = LogcatSettings.getInstance(project).state

  // Вид (Configure formatting): состояние для Compose, запись в настройки — при каждом изменении.
  var softWrap by mutableStateOf(settings.softWrap); private set
  var foldStacks by mutableStateOf(settings.foldStacks); private set
  var autoExpandJson by mutableStateOf(settings.autoExpandJson); private set
  var autoExpandUrls by mutableStateOf(settings.autoExpandUrls); private set
  var hideRepeatedTags by mutableStateOf(settings.hideRepeatedTags); private set
  var showTimestamp by mutableStateOf(settings.showTimestamp); private set
  var showPid by mutableStateOf(settings.showPid); private set
  var showTag by mutableStateOf(settings.showTag); private set
  var showPackage by mutableStateOf(settings.showPackage); private set
  var showLevel by mutableStateOf(settings.showLevel); private set
  var timestampFormat by mutableStateOf(settings.timestampFormat ?: "TIME"); private set
  var tagWidth by mutableStateOf(settings.tagWidth); private set
  var packageWidth by mutableStateOf(settings.packageWidth); private set
  var bufferLines by mutableStateOf(settings.bufferLines); private set
  var history by mutableStateOf<List<String>>(settings.history.toList()); private set
  var favorites by mutableStateOf<Set<String>>(settings.favorites.toSet()); private set


  fun applySoftWrap(v: Boolean) { softWrap = v; settings.softWrap = v }
  fun applyFoldStacks(v: Boolean) { foldStacks = v; settings.foldStacks = v }
  fun applyAutoExpandJson(v: Boolean) { autoExpandJson = v; settings.autoExpandJson = v }
  fun applyAutoExpandUrls(v: Boolean) { autoExpandUrls = v; settings.autoExpandUrls = v }
  fun applyHideRepeatedTags(v: Boolean) { hideRepeatedTags = v; settings.hideRepeatedTags = v }
  fun applyShowTimestamp(v: Boolean) { showTimestamp = v; settings.showTimestamp = v }
  fun applyShowPid(v: Boolean) { showPid = v; settings.showPid = v }
  fun applyShowTag(v: Boolean) { showTag = v; settings.showTag = v }
  fun applyShowPackage(v: Boolean) { showPackage = v; settings.showPackage = v }
  fun applyShowLevel(v: Boolean) { showLevel = v; settings.showLevel = v }
  fun applyTimestampFormat(v: String) { timestampFormat = v; settings.timestampFormat = v }
  fun applyTagWidth(v: Int) { tagWidth = v.coerceIn(6, 48); settings.tagWidth = tagWidth }
  fun applyPackageWidth(v: Int) { packageWidth = v.coerceIn(6, 48); settings.packageWidth = packageWidth }
  fun applyBufferLines(v: Int) { bufferLines = v; settings.bufferLines = v }

  /** Пресеты как «Standard / Compact» у штатного Logcat. */
  fun applyPreset(compact: Boolean) {
    applyShowTimestamp(true); applyTimestampFormat(if (compact) "TIME" else "DATE_TIME")
    applyShowPid(!compact && false); applyShowTag(true); applyShowPackage(!compact); applyShowLevel(true)
    applyHideRepeatedTags(compact)
  }

  /** Подхватывает значения, изменённые на странице Settings (там пишут прямо в [LogcatSettings]). */
  fun syncFromSettings() {
    softWrap = settings.softWrap
    foldStacks = settings.foldStacks
    autoExpandJson = settings.autoExpandJson
    autoExpandUrls = settings.autoExpandUrls
    hideRepeatedTags = settings.hideRepeatedTags
    showTimestamp = settings.showTimestamp
    showPid = settings.showPid
    showTag = settings.showTag
    showPackage = settings.showPackage
    showLevel = settings.showLevel
    timestampFormat = settings.timestampFormat ?: "TIME"
    tagWidth = settings.tagWidth
    packageWidth = settings.packageWidth
    bufferLines = settings.bufferLines
  }

  /** Запоминает фильтр в истории (по Enter / потере фокуса). */
  fun commitToHistory(query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    history = (listOf(q) + history.filter { it != q }).take(LogcatSettings.HISTORY_LIMIT)
    settings.history = history.toMutableList()
  }

  fun toggleFavorite(q: String) {
    favorites = if (q in favorites) favorites - q else favorites + q
    settings.favorites = favorites.toMutableList()
  }

  fun removeFromHistory(q: String) {
    history = history - q
    settings.history = history.toMutableList()
  }


  companion object {
    fun getInstance(project: Project): ViewOptions = project.service()
  }
}
