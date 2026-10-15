package com.cactus.logcactus.standalone

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer

object QuerySuggestionPopup {
  private val suggestions = linkedMapOf(
    "tag:" to "Log tag contains string",
    "is:" to "Filter by type of log message",
    "level:" to "Filter by minimum level",
    "message:" to "Log message contains string",
    "line:" to "Log line contains string",
    "process:" to "Process name or pid contains string",
    "package:" to "Package name contains string",
    "package:mine" to "Only logs for current package",
    "age:" to "Log age: age:30, age:5m, age:1h",
    "tag~:" to "Regex field modifier for tag",
    "-tag:" to "Negation prefix (exclude by key)",
    "is:crash" to "Likely app crash messages",
    "is:stacktrace" to "Java/Kotlin stack trace lines",
    "name:" to "Alias for package filter"
  )

  @JvmStatic
  fun install(field: JTextField): Runnable {
    val popup = JPopupMenu().apply { isBorderPainted = true }
    val all = suggestions.entries.map { SuggestionItem(it.key, it.value) }

    val model = DefaultListModel<SuggestionItem>()
    val list = JList(model).apply {
      visibleRowCount = 8
      font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
      cellRenderer = ListCellRenderer { _, value, _, isSelected, _ ->
        val panel = JPanel(BorderLayout(10, 0)).apply {
          border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
          isOpaque = true
        }
        val key = JLabel(value.key).apply { font = Font(Font.MONOSPACED, Font.PLAIN, 13) }
        val desc = JLabel(value.description).apply { font = Font(Font.SANS_SERIF, Font.PLAIN, 12) }
        if (isSelected) {
          panel.background = Color(56, 96, 163)
          key.foreground = Color.WHITE
          desc.foreground = Color(221, 232, 255)
        } else {
          panel.background = Color(42, 46, 54)
          key.foreground = Color(230, 234, 244)
          desc.foreground = Color(165, 173, 188)
        }
        panel.add(key, BorderLayout.WEST)
        panel.add(desc, BorderLayout.CENTER)
        panel
      }
    }

    val hint = JTextArea("Use keys: tag:, is:, level:, package:, process:, message:, age:.  Next tip: press Ctrl+Space").apply {
      isEditable = false
      isFocusable = false
      isOpaque = false
      font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
      foreground = Color(130, 140, 155)
    }

    val panel = JPanel(BorderLayout()).apply {
      add(JScrollPane(list).apply { preferredSize = Dimension(560, 220) }, BorderLayout.CENTER)
      add(hint, BorderLayout.SOUTH)
    }
    popup.add(panel)

    val refill = Runnable {
      val prefix = currentToken(field)
      model.clear()
      for (item in all) {
        if (prefix.isBlank() || item.key.startsWith(prefix) || item.key.contains(prefix)) {
          model.addElement(item)
        }
      }
      if (!model.isEmpty) list.selectedIndex = 0
    }

    val acceptSelection = Runnable {
      val selected = list.selectedValue ?: return@Runnable
      val text = field.text
      val caret = field.caretPosition

      var start = caret.coerceIn(0, text.length)
      while (start > 0 && !text[start - 1].isWhitespace()) start--

      var end = caret.coerceIn(0, text.length)
      while (end < text.length && !text[end].isWhitespace()) end++

      val replacement = if (selected.key.endsWith(":")) selected.key else "${selected.key} "
      val next = text.substring(0, start) + replacement + text.substring(end)
      field.text = next
      field.caretPosition = (start + replacement.length).coerceAtMost(next.length)
      popup.isVisible = false
    }

    list.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent) {
        if (e.clickCount == 2) acceptSelection.run()
      }
    })

    list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "accept")
    list.actionMap.put("accept", object : AbstractAction() {
      override fun actionPerformed(e: java.awt.event.ActionEvent) = acceptSelection.run()
    })

    val showPopup = Runnable {
      refill.run()
      if (model.isEmpty) return@Runnable
      if (!popup.isVisible) popup.show(field, 0, field.height + 2)
      list.requestFocusInWindow()
    }

    field.getInputMap(JComponent.WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_DOWN_MASK),
      "showQueryHelp"
    )
    field.actionMap.put("showQueryHelp", object : AbstractAction() {
      override fun actionPerformed(e: java.awt.event.ActionEvent) = showPopup.run()
    })

    field.addKeyListener(object : KeyAdapter() {
      override fun keyReleased(e: KeyEvent) {
        when {
          e.keyChar == ':' -> if (!popup.isVisible) showPopup.run()
          popup.isVisible -> refill.run()
          e.keyCode == KeyEvent.VK_ESCAPE -> popup.isVisible = false
        }
      }
    })

    return showPopup
  }

  private fun currentToken(field: JTextField): String {
    val text = field.text
    val caret = field.caretPosition.coerceIn(0, text.length)
    var start = caret
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    return text.substring(start, caret).trim()
  }

  private data class SuggestionItem(val key: String, val description: String)
}
