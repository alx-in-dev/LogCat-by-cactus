package com.cactus.logcactus.standalone

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/** Настройки вида лога (меню Configure view, кроме выбора процесса — процессы и PID меняются между запусками). */
data class ViewSettings(
  val showTime: Boolean = true,
  val showTag: Boolean = true,
  val showAppName: Boolean = true,
  val showProcess: Boolean = true,
  val timeWidth: Int = 12,
  val tagWidth: Int = 16,
  val appNameWidth: Int = 24,
  val processWidth: Int = 24,
  val fontSize: Int = 13,
  val wrapLines: Boolean = true,
  val foldStacks: Boolean = true,
  val autoExpandJson: Boolean = true,
  val hideDuplicateTags: Boolean = false,
  val autoExpandUrls: Boolean = true
)

/**
 * Хранит [ViewSettings] в `~/.logcactus/view.properties`. Отдельно от `preferences.properties` (там фильтр и уровень
 * пишет контроллер сессии), чтобы два писателя не затирали друг друга. Битые и отсутствующие значения — по умолчанию,
 * числа ограничиваются теми же пределами, что и кнопки «+/−».
 */
class ViewSettingsStore(private val file: Path = Paths.get(System.getProperty("user.home"), ".logcactus", "view.properties")) {
  fun load(): ViewSettings? {
    if (!Files.exists(file)) return null
    val props = Properties()
    val ok = runCatching { Files.newInputStream(file).use { props.load(it) } }.isSuccess
    if (!ok) return null
    val d = ViewSettings()
    fun bool(key: String, default: Boolean) = props.getProperty(key)?.trim()?.toBooleanStrictOrNull() ?: default
    fun int(key: String, default: Int, min: Int, max: Int) = (props.getProperty(key)?.trim()?.toIntOrNull() ?: default).coerceIn(min, max)
    return ViewSettings(
      showTime = bool("showTime", d.showTime),
      showTag = bool("showTag", d.showTag),
      showAppName = bool("showAppName", d.showAppName),
      showProcess = bool("showProcess", d.showProcess),
      timeWidth = int("timeWidth", d.timeWidth, 6, 32),
      tagWidth = int("tagWidth", d.tagWidth, 6, 48),
      appNameWidth = int("appNameWidth", d.appNameWidth, 6, 48),
      processWidth = int("processWidth", d.processWidth, 8, 64),
      fontSize = int("fontSize", d.fontSize, 8, 24),
      wrapLines = bool("wrapLines", d.wrapLines),
      foldStacks = bool("foldStacks", d.foldStacks),
      autoExpandJson = bool("autoExpandJson", d.autoExpandJson),
      hideDuplicateTags = bool("hideDuplicateTags", d.hideDuplicateTags),
      autoExpandUrls = bool("autoExpandUrls", d.autoExpandUrls)
    )
  }

  fun save(s: ViewSettings) = saveChanged(null, s)

  /**
   * Записывает только поля, которые вкладка изменила относительно [previous] (то, что она сама прочитала/записала
   * в прошлый раз); остальное берётся из файла — так вкладки не затирают друг у друга свежие настройки.
   */
  fun saveChanged(previous: ViewSettings?, s: ViewSettings) {
    val parent = file.parent ?: return
    if (runCatching { Files.createDirectories(parent) }.isFailure) return
    val next = toMap(s)
    val prev = previous?.let { toMap(it) }
    val disk = load()?.let { toMap(it) }
    val merged = Properties()
    for ((k, v) in next) merged.setProperty(k, if (prev == null || disk == null || v != prev[k]) v else disk[k] ?: v)
    runCatching { AtomicFiles.write(file) { merged.store(it, "LogCactus view settings") } }
  }

  private fun toMap(s: ViewSettings): Map<String, String> = linkedMapOf(
    "showTime" to s.showTime.toString(),
    "showTag" to s.showTag.toString(),
    "showAppName" to s.showAppName.toString(),
    "showProcess" to s.showProcess.toString(),
    "timeWidth" to s.timeWidth.toString(),
    "tagWidth" to s.tagWidth.toString(),
    "appNameWidth" to s.appNameWidth.toString(),
    "processWidth" to s.processWidth.toString(),
    "fontSize" to s.fontSize.toString(),
    "wrapLines" to s.wrapLines.toString(),
    "foldStacks" to s.foldStacks.toString(),
    "autoExpandJson" to s.autoExpandJson.toString(),
    "hideDuplicateTags" to s.hideDuplicateTags.toString(),
    "autoExpandUrls" to s.autoExpandUrls.toString()
  )
}
