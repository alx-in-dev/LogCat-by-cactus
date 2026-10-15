package com.cactus.logcactus.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.android.tools.idea.logcat.standalone.core.LogLevel
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** Цвета уровней — из цветовой схемы редактора (те же ключи, что у штатного Logcat), с запасными, если схема их не знает. */
class LevelColors(val messageFg: Color, val badgeFg: Color, val badgeBg: Color)

@Composable
fun levelColors(level: LogLevel): LevelColors {
  val dark = JewelTheme.isDark
  val normal = JewelTheme.globalColors.text.normal
  return remember(level, dark) {
    val name = when (level) {
      LogLevel.VERBOSE -> "VERBOSE"
      LogLevel.DEBUG -> "DEBUG"
      LogLevel.INFO -> "INFO"
      LogLevel.WARN -> "WARNING"
      LogLevel.ERROR -> "ERROR"
      LogLevel.ASSERT -> "ASSERT"
      LogLevel.UNKNOWN -> "VERBOSE"
    }
    val scheme = EditorColorsManager.getInstance().globalScheme
    val msg = TextAttributesKey.find("LOGCAT_V2_MESSAGE_$name").let { scheme.getAttributes(it) }
    val lvl = TextAttributesKey.find("LOGCAT_V2_LEVEL_$name").let { scheme.getAttributes(it) }
    val fallback = fallbackColor(level, dark)
    LevelColors(
      messageFg = msg?.foregroundColor?.toCompose() ?: fallback ?: normal,
      badgeFg = lvl?.foregroundColor?.toCompose() ?: Color.White,
      badgeBg = lvl?.backgroundColor?.toCompose() ?: (fallback ?: Color.Gray).copy(alpha = 0.85f)
    )
  }
}

private fun java.awt.Color.toCompose() = Color(red, green, blue, alpha)

private fun fallbackColor(level: LogLevel, dark: Boolean): Color? = when (level) {
  LogLevel.ERROR, LogLevel.ASSERT -> if (dark) Color(0xFFFF6B68) else Color(0xFFC7222D)
  LogLevel.WARN -> if (dark) Color(0xFFE2A93B) else Color(0xFF9B6400)
  LogLevel.INFO -> if (dark) Color(0xFF6A9955) else Color(0xFF2E7D32)
  LogLevel.DEBUG -> if (dark) Color(0xFF6C9BD1) else Color(0xFF1B6AC9)
  else -> null
}

private val TAG_PALETTE_DARK = listOf(0xFF6CB6FF, 0xFFC792EA, 0xFFF78C6C, 0xFF89DDFF, 0xFFFFCB6B, 0xFFA5D6A7, 0xFFEF9A9A, 0xFF80CBC4).map { Color(it) }
private val TAG_PALETTE_LIGHT = listOf(0xFF1565C0, 0xFF6A1B9A, 0xFFBF360C, 0xFF00695C, 0xFF8D6E00, 0xFF2E7D32, 0xFFAD1457, 0xFF00838F).map { Color(it) }

/** Стабильный цвет тега (по хэшу), чтобы одинаковые теги легко находились глазами. */
fun tagColor(tag: String, dark: Boolean): Color {
  val p = if (dark) TAG_PALETTE_DARK else TAG_PALETTE_LIGHT
  return p[(tag.hashCode() and Int.MAX_VALUE) % p.size]
}
