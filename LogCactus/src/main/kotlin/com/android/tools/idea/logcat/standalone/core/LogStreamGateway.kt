package com.android.tools.idea.logcat.standalone.core

import java.util.function.Consumer

interface LogStreamGateway {
  fun subscribe(deviceId: String?, listener: Consumer<LogEntry>): Subscription

  fun clear(deviceId: String?)
}
