package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.SavedLogcatFilter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class SavedFilterEditorDialog private constructor(
  owner: Window,
  initial: SavedLogcatFilter?
) : JDialog(if (owner is Frame) owner else null, "Edit Filter Configuration", true) {
  enum class Action {
    SAVE, DELETE, CANCEL
  }

  class Result(val action: Action, val filter: SavedLogcatFilter?)

  private val nameField = JTextField(24)
  private val messageField = JTextField(24)
  private val messageRegex = JCheckBox("Regex", true)
  private val levelBox = JComboBox(arrayOf("", "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR"))
  private val tagField = JTextField(24)
  private val tagRegex = JCheckBox("Regex", true)
  private val pidField = JTextField(24)
  private val packageField = JTextField(24)
  private val packageRegex = JCheckBox("Regex", true)

  private var result = Result(Action.CANCEL, null)

  init {
    defaultCloseOperation = DISPOSE_ON_CLOSE

    val form = JPanel(GridBagLayout())
    val c = GridBagConstraints().apply {
      insets = Insets(4, 6, 4, 6)
      anchor = GridBagConstraints.WEST
      fill = GridBagConstraints.HORIZONTAL
      weightx = 1.0
    }

    var row = 0
    addRow(form, c, row++, "Name", nameField, null)
    addRow(form, c, row++, "Message", messageField, messageRegex)
    addRow(form, c, row++, "Min level", levelBox, null)
    addRow(form, c, row++, "Tag", tagField, tagRegex)
    addRow(form, c, row++, "PID", pidField, null)
    addRow(form, c, row++, "Package", packageField, packageRegex)

    if (initial != null) {
      nameField.text = initial.name.orEmpty()
      messageField.text = initial.logMessagePattern.orEmpty()
      messageRegex.isSelected = initial.isLogMessageRegex
      levelBox.selectedItem = initial.logLevel.orEmpty()
      tagField.text = initial.logTagPattern.orEmpty()
      tagRegex.isSelected = initial.isLogTagRegex
      pidField.text = initial.pid.orEmpty()
      packageField.text = initial.packageNamePattern.orEmpty()
      packageRegex.isSelected = initial.isPackageNameRegex
    }

    val save = JButton("Save").apply {
      addActionListener {
        result = Result(Action.SAVE, collect())
        dispose()
      }
    }
    val delete = JButton("Delete").apply {
      isEnabled = initial?.name?.isNotBlank() == true
      addActionListener {
        result = Result(Action.DELETE, initial)
        dispose()
      }
    }
    val cancel = JButton("Cancel").apply { addActionListener { dispose() } }

    val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6)).apply {
      add(delete)
      add(cancel)
      add(save)
    }

    layout = BorderLayout()
    add(form, BorderLayout.CENTER)
    add(buttons, BorderLayout.SOUTH)
    pack()
    setLocationRelativeTo(owner)
  }

  private fun collect(): SavedLogcatFilter {
    return SavedLogcatFilter(
      nullable(nameField.text),
      nullable(messageField.text),
      messageRegex.isSelected,
      nullable(levelBox.selectedItem?.toString().orEmpty()),
      nullable(tagField.text),
      tagRegex.isSelected,
      nullable(pidField.text),
      nullable(packageField.text),
      packageRegex.isSelected
    )
  }

  companion object {
    @JvmStatic
    fun showDialog(owner: Window, initial: SavedLogcatFilter?): Result {
      val dialog = SavedFilterEditorDialog(owner, initial)
      dialog.isVisible = true
      return dialog.result
    }

    private fun addRow(
      form: JPanel,
      c: GridBagConstraints,
      row: Int,
      label: String,
      field: Component,
      extra: Component?
    ) {
      c.gridx = 0
      c.gridy = row
      c.weightx = 0.0
      form.add(JLabel(label), c)

      c.gridx = 1
      c.weightx = 1.0
      form.add(field, c)

      if (extra != null) {
        c.gridx = 2
        c.weightx = 0.0
        form.add(extra, c)
      }
    }

    private fun nullable(text: String?): String? {
      val trimmed = text?.trim().orEmpty()
      return if (trimmed.isEmpty()) null else trimmed
    }
  }
}
