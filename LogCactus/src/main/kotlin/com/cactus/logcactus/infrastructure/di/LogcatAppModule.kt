package com.cactus.logcactus.infrastructure.di

import com.android.tools.idea.logcat.standalone.core.PreferencesStore
import com.cactus.logcactus.standalone.AdbDeviceCatalog
import com.cactus.logcactus.standalone.DemoDeviceCatalog
import com.cactus.logcactus.standalone.DeviceCatalog
import com.cactus.logcactus.standalone.FilePreferencesStore
import com.cactus.logcactus.standalone.SimpleDeviceProvider
import org.koin.dsl.module
val logcatAppModule = module {
  single<DeviceCatalog> {
    val backend = System.getProperty("logcactus.backend", "adb").trim().lowercase()
    if (backend == "adb") AdbDeviceCatalog() else DemoDeviceCatalog()
  }

  single<SimpleDeviceProvider> {
    val packageName = System.getProperty("logcactus.package", "com.example.app")
    val pid = Integer.getInteger("logcactus.pid", 12345)
    val devices = get<DeviceCatalog>().listDevices()
    if (devices.isEmpty()) {
      SimpleDeviceProvider("no-device", "No Devices", pid, packageName)
    } else {
      val first = devices.first()
      SimpleDeviceProvider(first.id, first.displayName, pid, packageName)
    }
  }

  single<PreferencesStore> { FilePreferencesStore() }
}
