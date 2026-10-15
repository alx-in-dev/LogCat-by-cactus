package com.android.tools.idea.logcat.standalone.core

import java.util.concurrent.atomic.AtomicBoolean

class LogcatSessionController(
  private val devices: DeviceProvider,
  private val logs: LogStreamGateway,
  private val preferences: PreferencesStore,
  private val ui: UiHost
) {
  private val started = AtomicBoolean(false)
  private var deviceSubscription: Subscription? = null
  private var logSubscription: Subscription? = null
  // Все изменения и render — под замком this: поток logcat, UI и listener устройств пишут state одновременно.
  private var generation = 0
  /** Какие записи сейчас показываются фильтром (null — не знаем): при переполнении буфера остальные вытесняются первыми. */
  @Volatile var visibleEntryPredicate: (() -> ((LogEntry) -> Boolean)?)? = null
  private var state: LogcatUiState = LogcatUiState.initial(preferences.load())

  fun start() {
    if (!started.compareAndSet(false, true)) return

    deviceSubscription = devices.addListener(object : DeviceSelectionListener {
      override fun onSelectedDeviceChanged(deviceRef: DeviceRef?) {
        reconnectLogStream(deviceRef)
      }

      override fun onSelectedClientChanged(clientRef: ClientRef?) {
        synchronized(this@LogcatSessionController) {
          state = state.withSelection(state.selectedDevice, clientRef)
          ui.render(state)
        }
      }
    })

    val selectedDevice = devices.selectedDevice()
    val selectedClient = devices.selectedClient()
    synchronized(this) { state = state.withSelection(selectedDevice, selectedClient) }
    reconnectLogStream(selectedDevice)
    synchronized(this) { ui.render(state) }
  }

  fun restart() {
    if (!started.get()) return
    reconnectLogStream(devices.selectedDevice())
  }

  fun stop() {
    if (!started.compareAndSet(true, false)) return

    unsubscribe(logSubscription)
    unsubscribe(deviceSubscription)
    logSubscription = null
    deviceSubscription = null
  }

  fun onAction(action: LogcatAction) = synchronized(this) {
    when (action) {
      is LogcatAction.ApplyFilter -> {
        state = state.withFilter(
          action.level,
          action.configuredFilterName,
          action.query,
          action.isRegex,
          action.isCaseSensitive
        )
        preferences.save(
          LogcatUiPreferences(
            action.level,
            action.configuredFilterName,
            action.query,
            action.isRegex,
            action.isCaseSensitive,
            state.maxBytes
          )
        )
        ui.render(state)
      }

      is LogcatAction.SetBufferSize -> {
        state = state.withBufferLimit(action.bytes)
        preferences.save(
          LogcatUiPreferences(state.minLevel, state.configuredFilterName, state.query, state.isRegex, state.isCaseSensitive, state.maxBytes)
        )
        ui.render(state)
      }

      is LogcatAction.ClearLogs -> {
        state = state.clearLogs()
        ui.render(state)
        // `adb logcat -c` может надолго зависнуть: не на Swing-потоке и не под замком, иначе встанут и кнопки, и приём логов.
        state.selectedDevice?.let { device ->
          Thread({ runCatching { logs.clear(device.id) } }, "logcactus-clear").apply { isDaemon = true }.start()
        }
      }
    }
  }

  private fun reconnectLogStream(device: DeviceRef?) {
    val mine: Int
    synchronized(this) {
      unsubscribe(logSubscription)
      logSubscription = null
      mine = ++generation
      state = state.withSelection(device, devices.selectedClient()).clearLogs()
      if (device == null) {
        ui.render(state)
        return
      }
    }
    device ?: return

    try {
      val subscription = logs.subscribe(device.id) { entry ->
        synchronized(this) {
          // Запись от прежней подписки, дочитывавшей хвост после переподключения, в новый буфер не попадает.
          if (mine != generation) return@synchronized
          state = state.append(entry, visibleEntryPredicate)
          ui.render(state)
        }
      }
      synchronized(this) { if (mine == generation) logSubscription = subscription else subscription.unsubscribe() }
    } catch (exception: RuntimeException) {
      ui.showError(exception.message ?: "Unable to subscribe to log stream")
    }
  }

  private fun unsubscribe(subscription: Subscription?) {
    subscription?.unsubscribe()
  }
}
