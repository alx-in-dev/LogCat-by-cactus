package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.DeviceRef

interface DeviceCatalog {
  fun listDevices(): List<DeviceRef>
}
