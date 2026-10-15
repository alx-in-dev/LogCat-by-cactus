package com.cactus.logcactus.standalone

import com.android.tools.idea.adb.AdbFileProvider

/** Подмена `AdbLocator` основного приложения для общего кода (`DeviceActions`, `WifiAdb`): adb — тот, что использует Студия. */
object AdbLocator {
  fun path(): String = AdbFileProvider.fromApplication()?.get()?.path ?: "adb"
}
