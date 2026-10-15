package com.cactus.logcactus.presentation.logcat

/**
 * Достаточно для общего кода из основного приложения (`CrashTracker`): там `StackFolder` — большой Compose-класс,
 * в плагин он не входит, а нужна только эта проверка кадра стектрейса (копия `StackFolder.isFrame`).
 */
object StackFolder {
  fun isFrame(message: String): Boolean {
    var i = 0
    while (i < message.length && (message[i] == ' ' || message[i] == '\t')) i++
    if (i == message.length) return false
    if (message.startsWith("at ", i)) return message.endsWith(")") || message.endsWith(")\r")
    return message.startsWith("... ", i) && message.endsWith(" more")
  }
}
