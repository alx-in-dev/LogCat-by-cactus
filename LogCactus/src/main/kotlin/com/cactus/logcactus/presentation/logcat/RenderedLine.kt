package com.cactus.logcactus.presentation.logcat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.android.tools.idea.logcat.standalone.core.LogEntry

data class RenderedLine(
  val text: AnnotatedString,
  val message: String,
  val color: Color,
  val level: String,
  val badgeBg: Color,
  val badgeFg: Color,
  // Исходная запись (по идентичности): по ней строка связывается с закладкой.
  val entry: LogEntry,
  // Стабильный ключ для LazyColumn: без него при сдвиге окна логов каждый индекс получал
  // другое содержимое и перекомпонировались все видимые строки на каждом кадре.
  val id: Long = NEXT_ID.incrementAndGet(),
  // Не null у служебных строк сворачивания стектрейса и у строк-раскрытий JSON/Intent.
  val fold: RowFold? = null
) {
  private companion object {
    val NEXT_ID = java.util.concurrent.atomic.AtomicLong()
  }
}

/**
 * STACK: строка-заглушка серии кадров стектрейса (`hidden` — сами кадры; `expanded` — кадры
 * показаны, а заглушка превращается в кнопку "свернуть"). DETAIL: дочерняя строка раскрытия
 * JSON/Intent, производная от исходной и в копируемый текст не входит. HEAD: исходная строка с раскрытым
 * JSON/Intent, в которой остался только префикс (`hidden` — сама исходная строка для копирования).
 */
class RowFold(val kind: Kind, val key: LogEntry, val expanded: Boolean, val hidden: List<RenderedLine>) {
  enum class Kind { STACK, DETAIL, HEAD }
}
