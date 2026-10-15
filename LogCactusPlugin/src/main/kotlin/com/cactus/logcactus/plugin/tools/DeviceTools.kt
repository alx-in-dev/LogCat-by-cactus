package com.cactus.logcactus.plugin.tools

import com.android.tools.idea.logcat.standalone.core.LogEntry
import com.cactus.logcactus.plugin.LogcatSession
import com.cactus.logcactus.presentation.logcat.SessionDiff
import com.cactus.logcactus.standalone.ActionResult
import com.cactus.logcactus.standalone.DeviceActions
import com.cactus.logcactus.standalone.ForegroundAppParser
import com.cactus.logcactus.standalone.ForegroundAppTracker
import com.cactus.logcactus.standalone.SessionFile
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Действия над устройством и приложением (adb Студии), сессии и подключение по Wi-Fi; результат — уведомлением. */
class DeviceTools(
  private val project: Project,
  private val session: LogcatSession,
  private val scope: CoroutineScope,
  /** Открывает записи из файла сессии в новой вкладке. */
  private val openSessionTab: (name: String, entries: List<LogEntry>) -> Unit
) {
  private val actions = DeviceActions()
  private val tracker = ForegroundAppTracker()

  private fun notify(message: String, type: NotificationType = NotificationType.INFORMATION, action: NotificationAction? = null) {
    val n = NotificationGroupManager.getInstance().getNotificationGroup("LogCactus").createNotification(message, type)
    action?.let { n.addAction(it) }
    n.notify(project)
  }

  private fun report(r: ActionResult) = notify(r.message, if (r.ok) NotificationType.INFORMATION else NotificationType.WARNING)

  private fun ui(block: () -> Unit) = ApplicationManager.getApplication().invokeLater(block)

  /** Пакет, над которым работают действия: приложение на экране устройства (или закреплённое `package:mine`). */
  suspend fun targetPackage(): String? = withContext(Dispatchers.IO) {
    val serial = session.selected?.serial ?: return@withContext null
    session.minePackage.ifBlank { null }
      ?: tracker.current(serial)?.takeIf { !ForegroundAppParser.isSystemShell(it) }
  }

  private fun onTarget(block: suspend (serial: String, pkg: String) -> Unit) {
    val serial = session.selected?.serial ?: return notify("No device selected", NotificationType.WARNING)
    scope.launch {
      val pkg = targetPackage() ?: return@launch notify("Could not determine the app on the device screen", NotificationType.WARNING)
      block(serial, pkg)
    }
  }

  fun restartApp() = onTarget { s, p -> report(withContext(Dispatchers.IO) { actions.restart(s, p) }) }
  fun forceStop() = onTarget { s, p -> report(withContext(Dispatchers.IO) { actions.forceStop(s, p) }) }
  fun clearCache() = onTarget { s, p -> report(withContext(Dispatchers.IO) { actions.clearCache(s, p) }) }

  fun clearData() = onTarget { s, p ->
    val ok = withContext(Dispatchers.EDT) {
      Messages.showYesNoDialog(project, "All data of $p (accounts, databases, settings) will be erased. This cannot be undone.", "Clear App Data", "Clear data", "Cancel", Messages.getWarningIcon()) == Messages.YES
    }
    if (ok) report(withContext(Dispatchers.IO) { actions.clearData(s, p) })
  }

  fun openDeepLink() = onTarget { s, p ->
    val uri = withContext(Dispatchers.EDT) { Messages.showInputDialog(project, "URI to open on the device:", "Open Deep Link", null, "", null) }
    if (!uri.isNullOrBlank()) report(withContext(Dispatchers.IO) { actions.openDeepLink(s, uri, p) })
  }

  fun appInfo() = onTarget { s, p ->
    val details = withContext(Dispatchers.IO) { actions.appDetails(s, p) }
    withContext(Dispatchers.EDT) {
      if (details == null) Messages.showInfoMessage(project, "No details for $p", "App info") else Messages.showInfoMessage(project, details.toText(), "App info")
    }
  }

  fun screenshot() {
    val serial = session.selected?.serial ?: return notify("No device selected", NotificationType.WARNING)
    scope.launch {
      val file = withContext(Dispatchers.IO) { actions.screenshot(serial)?.let { actions.saveScreenshot(it) } }
      if (file == null) notify("Screenshot failed", NotificationType.WARNING)
      else notify("Screenshot saved: ${file.name}", action = NotificationAction.createSimple("Show in folder") { RevealFileAction.openFile(file) })
    }
  }

  // ---- сессии ----------------------------------------------------------------------------------

  fun saveSession() {
    val all = session.bufferSnapshot()
    if (all.isEmpty()) return notify("Nothing to save: the log is empty", NotificationType.WARNING)
    val choice = Messages.showDialog(project, "Which lines to save?", "Save Session", arrayOf("Visible by filter", "Whole buffer", "Cancel"), 0, Messages.getQuestionIcon())
    if (choice < 0 || choice > 1) return
    val entries = if (choice == 0) session.visible else all
    val name = "logcactus-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "." + SessionFile.EXTENSION
    val target = FileChooserFactory.getInstance()
      .createSaveFileDialog(FileSaverDescriptor("Save Session", "Choose a file", SessionFile.EXTENSION), project)
      .save(null as com.intellij.openapi.vfs.VirtualFile?, name)?.file ?: return
    scope.launch(Dispatchers.IO) {
      runCatching { SessionFile.write(target, mapOf("device" to (session.selected?.title ?: ""), "filter" to session.query), entries) }
        .onSuccess { notify("Session saved: ${target.name} (${entries.size} lines)") }
        .onFailure { notify("Could not save the session: ${it.message}", NotificationType.ERROR) }
    }
  }

  private fun chooseSession(title: String): File? {
    val vf = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor(SessionFile.EXTENSION).withTitle(title), project, null) ?: return null
    return File(vf.path)
  }

  fun openSession() {
    val file = chooseSession("Open Session") ?: return
    scope.launch {
      val s = withContext(Dispatchers.IO) { runCatching { SessionFile.read(file) }.getOrNull() }
      if (s == null) notify("${file.name} is not a LogCactus session", NotificationType.WARNING) else openSessionTab(file.name, s.entries)
    }
  }

  fun compareWithSession() {
    val file = chooseSession("Compare With Session") ?: return
    scope.launch {
      val base = withContext(Dispatchers.IO) { runCatching { SessionFile.read(file) }.getOrNull() }
      if (base == null) return@launch notify("${file.name} is not a LogCactus session", NotificationType.WARNING)
      val current: List<LogEntry> = session.bufferSnapshot()
      val md = withContext(Dispatchers.Default) { SessionDiff.toMarkdown(SessionDiff.compute(base.entries, current), file.name, "current buffer") }
      withContext(Dispatchers.EDT) { ReportDialog(project, "Comparison", md).show() }
    }
  }

  fun connectWifi() = ui { WifiDialog(project, session, scope).show() }

  fun disconnectWifi() {
    val serial = session.selected?.serial ?: return
    scope.launch { report(withContext(Dispatchers.IO) { com.cactus.logcactus.standalone.WifiAdb().disconnect(serial) }) }
  }
}
