package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.DeviceRef

class DemoDeviceCatalog : DeviceCatalog {
  override fun listDevices(): List<DeviceRef> = listOf(
    DeviceRef(
      id = "demo-emulator-5554",
      displayName = "Demo Emulator",
      androidVersion = "14",
      apiLevel = "34",
      isEmulator = true
    )
  )
}
