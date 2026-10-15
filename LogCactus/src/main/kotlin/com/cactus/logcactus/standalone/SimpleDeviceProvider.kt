package com.cactus.logcactus.standalone

import com.android.tools.idea.logcat.standalone.core.ClientRef
import com.android.tools.idea.logcat.standalone.core.DeviceProvider
import com.android.tools.idea.logcat.standalone.core.DeviceRef
import com.android.tools.idea.logcat.standalone.core.DeviceSelectionListener
import com.android.tools.idea.logcat.standalone.core.Subscription
import java.util.concurrent.CopyOnWriteArrayList

class SimpleDeviceProvider(
  deviceId: String = "demo-emulator-5554",
  deviceName: String = "Demo Emulator",
  pid: Int = 12345,
  packageName: String = "com.example.app"
) : DeviceProvider {
  private val listeners = CopyOnWriteArrayList<DeviceSelectionListener>()

  private var selectedDevice: DeviceRef? = DeviceRef(deviceId, deviceName)
  private var selectedClient: ClientRef? = ClientRef(pid, packageName)

  override fun selectedDevice(): DeviceRef? = selectedDevice

  override fun selectedClient(): ClientRef? = selectedClient

  override fun addListener(listener: DeviceSelectionListener): Subscription {
    listeners.add(listener)
    return Subscription { listeners.remove(listener) }
  }

  fun selectDevice(device: DeviceRef?) {
    selectedDevice = device
    listeners.forEach { it.onSelectedDeviceChanged(device) }
  }

  fun selectClient(client: ClientRef?) {
    selectedClient = client
    listeners.forEach { it.onSelectedClientChanged(client) }
  }

  /** Выставляет выбор без уведомления слушателей (провайдер только что создан, слушателей нет). */
  fun selectDeviceSilently(device: DeviceRef?, client: ClientRef?) {
    selectedDevice = device
    selectedClient = client
  }
}
