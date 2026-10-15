package com.android.tools.idea.logcat.standalone.core

interface DeviceProvider {
  fun selectedDevice(): DeviceRef?

  fun selectedClient(): ClientRef?

  fun addListener(listener: DeviceSelectionListener): Subscription
}
