package com.cactus.logcactus.standalone

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Нативные диалоги выбора файла (вызывать из UI-потока). */
object FileChoosers {
  /** [extension] без точки: если пользователь стёр или не указал расширение, оно дописывается. */
  fun save(title: String, defaultName: String, extension: String? = null): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val finalName = if (extension != null && File(name).extension.isEmpty()) "$name.$extension" else name
    return File(dialog.directory, finalName)
  }

  fun open(title: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
  }
}
