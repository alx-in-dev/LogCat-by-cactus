package com.cactus.logcactus.standalone

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Запись через временный файл и переименование: сбой посреди записи не оставит наполовину записанный файл. */
object AtomicFiles {
  fun write(target: Path, body: (OutputStream) -> Unit) {
    target.parent?.let { Files.createDirectories(it) }
    val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
    try {
      Files.newOutputStream(tmp).use(body)
      try {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      runCatching { Files.deleteIfExists(tmp) }
    }
  }

  fun writeLines(target: Path, lines: List<String>) = write(target) { out ->
    out.write(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8))
  }
}
