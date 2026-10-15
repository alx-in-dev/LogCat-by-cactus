package com.cactus.logcactus.standalone

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Dark-theme foreground colors from Android Studio's real tag palette
 * (logcat/resources/palette/logcat-tags-palette.json, JetBrains/android,
 * Apache-2.0) — the same colors LogcatColors.getTagColor() picks from via
 * tag.hashCode(). Background colors from that palette are intentionally
 * unused there too (only foreground is applied to the tag text).
 */
object TagPalette {
  private val COLORS = listOf(
    "929292", "a3a3a3", "b5b5b5", "c7c7c7", "d9d9d9",
    "A9B7C6", "b9c4d0", "c8d1da", "d7dee4", "e7eaef",
    "D28546", "d7935b", "dca06f", "e1ae84", "e6bb98",
    "A8C023", "c0d930", "c9df4e", "d2e46c", "dbea8b",
    "AE8ABE", "b999c7", "c4a9d0", "d0bad9", "dbcae2",
    "D27981", "d6858b", "da9096", "de9ca1", "e2a7ac",
    "3AA192", "41b4a3", "4fbfaf", "62c6b7", "74cdc0",
    "FFC66D", "ffbb4d", "ffaf2e", "ffa30f", "f09400",
    "029CDE", "02aef7", "17b8fd", "30bffd", "49c7fd",
    "19A492", "1bb19d", "1dbfa9", "1eccb5", "20d9c1",
    "A388B4", "ab91ba", "b29bc0", "b9a4c6", "c0adcc",
    "B78A57", "bd9465", "c39e74", "c9a882", "d0b290",
    "7292CA", "819ecf", "90a9d5", "9fb5db", "aec0e0",
    "BBB529", "b4af27", "aba726", "a39f24", "9b9722",
    "D36FB1", "d77eba", "dc8ec2", "e19dcb", "e6add3",
    "4291FF", "579dff", "6ba9ff", "80b5ff", "94c1ff"
  ).map { Color(("FF" + it).toLong(16).toInt()) }

  fun colorFor(tag: String): Color {
    if (tag.isEmpty()) return COLORS[0]
    return COLORS[abs(tag.hashCode()) % COLORS.size]
  }
}
