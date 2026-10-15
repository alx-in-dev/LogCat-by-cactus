package com.cactus.logcactus.plugin

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.plugin.tools.DeviceTools
import com.cactus.logcactus.plugin.ui.LogcatPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.jewel.bridge.addComposeTab

/**
 * Вкладки окна LogCactus — штатные вкладки окна Студии (как у оригинального Logcat), «+» стоит сразу за ними.
 * Живые вкладки (устройство + фильтр) восстанавливаются после перезапуска; вкладки из файла сессии — нет.
 * У каждой вкладки своя [LogcatSession] со своим scope; закрытие вкладки останавливает её поток adb.
 */
class LogcatTabs(private val project: Project, private val toolWindow: ToolWindow) {
  private val settings = LogcatSettings.getInstance(project).state
  private val options = ViewOptions.getInstance(project)
  private val monitor = DeviceMonitor.getInstance(project)
  private val liveIds = HashMap<Content, String>() // вкладка → id в настройках
  private var counter = 0

  fun install() {
    toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        liveIds.remove(event.content)?.let { id -> settings.tabs.removeAll { it.id == id }; settings.touch() }
        // Окно без вкладок бесполезно — оставляем одну живую.
        if (toolWindow.contentManager.contentCount == 0 && !project.isDisposed && !Disposer.isDisposed(toolWindow.disposable)) addLiveTab()
      }
    })
    // «+» сразу за вкладками (tab actions), как в оригинальном Logcat.
    (toolWindow as? ToolWindowEx)?.setTabActions(object : DumbAwareAction("New Logcat Tab", "Open another logcat tab", AllIcons.General.Add) {
      override fun actionPerformed(e: AnActionEvent) { addLiveTab() }
    })
    val saved = settings.tabs.toList()
    if (saved.isEmpty()) addLiveTab() else saved.forEach { addLiveTab(it) }
  }

  private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private fun nextTitle(): String {
    counter++
    return if (counter == 1) "Logcat" else "Logcat ($counter)"
  }

  /** Новая живая вкладка; [restored] — сохранённое состояние (фильтр и устройство). */
  fun addLiveTab(restored: LogcatSettings.TabState? = null) {
    val state = restored ?: LogcatSettings.TabState().also { it.id = UUID.randomUUID().toString(); settings.tabs.add(it); settings.touch() }
    val scope = newScope()
    val session = LogcatSession(
      project, scope, options, monitor,
      initialQuery = state.query.orEmpty(),
      preferredSerial = state.serial?.takeIf { it.isNotBlank() }
    )
    session.onChangedHook = {
      state.query = session.query
      state.serial = session.selected?.serial.orEmpty()
      settings.touch() // вложенное изменение иначе не заставит платформу сохранить настройки
    }
    val tools = DeviceTools(project, session, scope) { name, entries -> addSessionTab(name, entries) }
    val title = state.name?.takeIf { it.isNotBlank() }?.also { counter++ } ?: nextTitle()
    attach(title, session, tools, state.id.orEmpty(), state)
  }

  /** Вкладка с записями из файла: без устройства и опроса adb. */
  fun addSessionTab(fileName: String, entries: List<LogEntry>) {
    val scope = newScope()
    val session = LogcatSession(project, scope, options, monitor, live = false)
    session.showSession(entries, fileName)
    attach("Session: $fileName", session, DeviceTools(project, session, scope) { n, e -> addSessionTab(n, e) }, null, null)
  }

  private fun attach(title: String, session: LogcatSession, tools: DeviceTools, persistedId: String?, state: LogcatSettings.TabState?) {
    toolWindow.addComposeTab(title, isLockable = true, isCloseable = true, focusOnClickInside = true) { LogcatPanel(session, tools, options) }
    val content = toolWindow.contentManager.contents.last()
    persistedId?.let { liveIds[content] = it }
    // «Rename Tab» из меню вкладки (штатное): запоминаем имя, чтобы оно пережило перезапуск Студии.
    if (state != null) {
      content.addPropertyChangeListener { e ->
        if (e.propertyName == Content.PROP_DISPLAY_NAME) {
          state.name = content.displayName.orEmpty()
          settings.touch()
        }
      }
    }
    val d: Disposable = Disposer.newDisposable("LogCactus tab $title")
    Disposer.register(d) { session.dispose() }
    content.setDisposer(d)
    toolWindow.contentManager.setSelectedContent(content)
  }
}
