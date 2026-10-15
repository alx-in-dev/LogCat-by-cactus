package com.android.tools.idea.logcat.standalone.core

interface DeviceSelectionListener {
  fun onSelectedDeviceChanged(deviceRef: DeviceRef?)

  fun onSelectedClientChanged(clientRef: ClientRef?)
}
