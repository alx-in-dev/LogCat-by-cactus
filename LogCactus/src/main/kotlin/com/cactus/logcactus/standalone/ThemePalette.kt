package com.cactus.logcactus.standalone

import java.awt.Color
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

class ThemePalette(
  @JvmField val uiBgEven: Color,
  @JvmField val uiBgOdd: Color,
  @JvmField val uiBgHover: Color,
  @JvmField val uiBgActive: Color,
  @JvmField val uiTextActive: Color,
  @JvmField val uiTextPrimary: Color,
  @JvmField val uiTextMuted: Color,
  @JvmField val uiSeparator: Color,
  @JvmField val uiAccent: Color,
  @JvmField val levelError: Color,
  @JvmField val levelWarn: Color,
  @JvmField val levelInfo: Color,
  @JvmField val levelDebug: Color,
  @JvmField val levelVerbose: Color,
  @JvmField val levelAssert: Color,
  @JvmField val badgeErrorBg: Color,
  @JvmField val badgeErrorFg: Color,
  @JvmField val badgeWarnBg: Color,
  @JvmField val badgeWarnFg: Color,
  @JvmField val badgeInfoBg: Color,
  @JvmField val badgeInfoFg: Color,
  @JvmField val badgeDebugBg: Color,
  @JvmField val badgeDebugFg: Color,
  @JvmField val badgeVerboseBg: Color,
  @JvmField val badgeVerboseFg: Color,
  @JvmField val badgeAssertBg: Color,
  @JvmField val badgeAssertFg: Color
) {
  companion object {
    private val ENTRY = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"(#?[0-9a-fA-F]{6})\"")

    @JvmStatic
    fun load(): ThemePalette {
      val defaults = defaults()
      val config = resolveConfigPath()
      if (config == null || !Files.exists(config)) return defaults

      val values = runCatching {
        parseColorMap(Files.readString(config, StandardCharsets.UTF_8))
      }.getOrNull() ?: return defaults

      return ThemePalette(
        color(values, "uiBgEven", defaults.uiBgEven),
        color(values, "uiBgOdd", defaults.uiBgOdd),
        color(values, "uiBgHover", defaults.uiBgHover),
        color(values, "uiBgActive", defaults.uiBgActive),
        color(values, "uiTextActive", defaults.uiTextActive),
        color(values, "uiTextPrimary", defaults.uiTextPrimary),
        color(values, "uiTextMuted", defaults.uiTextMuted),
        color(values, "uiSeparator", defaults.uiSeparator),
        color(values, "uiAccent", defaults.uiAccent),
        color(values, "levelError", defaults.levelError),
        color(values, "levelWarn", defaults.levelWarn),
        color(values, "levelInfo", defaults.levelInfo),
        color(values, "levelDebug", defaults.levelDebug),
        color(values, "levelVerbose", defaults.levelVerbose),
        color(values, "levelAssert", defaults.levelAssert),
        color(values, "badgeErrorBg", defaults.badgeErrorBg),
        color(values, "badgeErrorFg", defaults.badgeErrorFg),
        color(values, "badgeWarnBg", defaults.badgeWarnBg),
        color(values, "badgeWarnFg", defaults.badgeWarnFg),
        color(values, "badgeInfoBg", defaults.badgeInfoBg),
        color(values, "badgeInfoFg", defaults.badgeInfoFg),
        color(values, "badgeDebugBg", defaults.badgeDebugBg),
        color(values, "badgeDebugFg", defaults.badgeDebugFg),
        color(values, "badgeVerboseBg", defaults.badgeVerboseBg),
        color(values, "badgeVerboseFg", defaults.badgeVerboseFg),
        color(values, "badgeAssertBg", defaults.badgeAssertBg),
        color(values, "badgeAssertFg", defaults.badgeAssertFg)
      )
    }

    @JvmStatic
    fun resolveConfigPath(): Path? {
      val fromProperty = System.getProperty("logcactus.theme.path", "").trim()
      if (fromProperty.isNotEmpty()) return Paths.get(fromProperty)
      val home = System.getProperty("user.home", "").trim()
      if (home.isEmpty()) return null
      return Paths.get(home, ".logcactus", "theme.json")
    }

    @JvmStatic
    fun lastModified(path: Path?): Long {
      if (path == null || !Files.exists(path)) return -1L
      return runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(-1L)
    }

    // Level/message and badge colors below are taken verbatim from Android Studio's
    // real "New Logcat" (V2) Darcula color scheme (LogcatColorSchemeDarcula.xml,
    // JetBrains/android, Apache-2.0), not approximated.
    private fun defaults(): ThemePalette {
      return ThemePalette(
        rgb("1f2329"),
        rgb("1c2026"),
        rgb("252d38"),
        rgb("2f4b74"),
        rgb("eaf2ff"),
        rgb("dde4f1"),
        rgb("959fb0"),
        rgb("2b313b"),
        rgb("3f6ca7"),
        rgb("ff6b68"),
        rgb("bbb529"),
        rgb("ABC023"),
        rgb("299999"),
        rgb("bbbbbb"),
        rgb("ff6b68"),
        rgb("cf5b56"), rgb("000000"),
        rgb("bbb529"), rgb("000000"),
        rgb("6a8759"), rgb("e9f5e6"),
        rgb("305d78"), rgb("bbbbbb"),
        rgb("d6d6d6"), rgb("000000"),
        rgb("8b3c3c"), rgb("ffffff")
      )
    }

    private fun parseColorMap(json: String): Map<String, String> {
      val result = HashMap<String, String>()
      val matcher = ENTRY.matcher(json)
      while (matcher.find()) {
        result[matcher.group(1)] = matcher.group(2)
      }
      return result
    }

    private fun color(values: Map<String, String>, key: String, fallback: Color): Color {
      val raw = values[key]
      if (raw.isNullOrBlank()) return fallback
      val hex = if (raw.startsWith("#")) raw.substring(1) else raw
      if (hex.length != 6) return fallback
      return runCatching { rgb(hex) }.getOrDefault(fallback)
    }

    private fun rgb(hex: String): Color = Color(Integer.parseInt(hex, 16))
  }
}
