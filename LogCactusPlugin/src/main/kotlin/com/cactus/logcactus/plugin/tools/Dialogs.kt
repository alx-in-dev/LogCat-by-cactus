package com.cactus.logcactus.plugin.tools

import com.cactus.logcactus.plugin.LogcatSession
import com.cactus.logcactus.standalone.MdnsService
import com.cactus.logcactus.standalone.WifiAdb
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Текстовый отчёт с кнопкой копирования (сравнение сессий). */
class ReportDialog(project: Project, title: String, private val text: String) : DialogWrapper(project) {
  init { this.title = title; init() }

  override fun createCenterPanel(): JComponent {
    val area = JBTextArea(text).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    return JBScrollPane(area).apply { preferredSize = Dimension(760, 520) }
  }

  override fun createActions(): Array<Action> = arrayOf(
    object : DialogWrapperAction("Copy") { override fun doAction(e: java.awt.event.ActionEvent?) { CopyPasteManager.getInstance().setContents(StringSelection(text)) } },
    okAction
  )
}

/** Подключение по Wi-Fi: быстрый режим из USB, сопряжение по коду (Android 11+), подключение, поиск в сети. */
class WifiDialog(private val project: Project, private val session: LogcatSession, private val scope: CoroutineScope) : DialogWrapper(project) {
  private val wifi = WifiAdb()
  private val status = JBLabel(" ")
  private val pairAddress = JBTextField(22)
  private val pairCode = JBTextField(8)
  private val connectAddress = JBTextField(22)
  private val services = JBLabel(" ")

  init { title = "Connect via Wi-Fi"; init() }

  private fun run(block: () -> String) {
    status.text = "working…"
    scope.launch(Dispatchers.IO) {
      val message = runCatching(block).getOrElse { "Failed: ${it.message}" }
      withContext(Dispatchers.EDT) { status.text = message }
    }
  }

  override fun createCenterPanel(): JComponent = panel {
    group("1. Phone is connected by USB") {
      row { comment("Switches the selected USB device to network mode and connects to its Wi-Fi address (same network required).") }
      row {
        button("Enable Wi-Fi for ${session.selected?.serial ?: "the selected device"}") {
          val serial = session.selected?.serial
          if (serial != null) run { wifi.enableFromUsb(serial).first.message }
        }.enabled(session.selected?.serial?.contains(':') == false)
      }
    }
    group("2. Pair (Android 11+, no cable)") {
      row { comment("On the phone: Developer options → Wireless debugging → Pair device with pairing code.") }
      row("Address:") { cell(pairAddress).align(AlignX.FILL) }
      row("Code:") { cell(pairCode); button("Pair") { run { wifi.pair(pairAddress.text.trim(), pairCode.text.trim()).message } } }
    }
    group("3. Connect") {
      row { comment("IP address and port from the main Wireless debugging screen (or 5555 after step 1).") }
      row("Address:") { cell(connectAddress).align(AlignX.FILL); button("Connect") { run { wifi.connect(connectAddress.text.trim()).message } } }
    }
    group("Found on the network") {
      row { button("Scan") {
        run {
          val found: List<MdnsService> = wifi.scan()
          SwingUtilities.invokeLater { services.text = if (found.isEmpty()) "Nothing found — turn on Wireless debugging on the phone" else found.joinToString("<br>", "<html>", "</html>") { "${it.name} — ${it.hostPort}${if (it.pairing) " (pairing)" else ""}" } }
          "Scan finished"
        }
      } }
      row { cell(services) }
    }
    row { cell(status) }
  }
}
