package com.cactus.logcactus.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.logcactus.plugin.ViewOptions
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.items
import org.jetbrains.jewel.ui.component.separator

private val BUFFER_SIZES = listOf(10_000, 20_000, 50_000, 100_000, 200_000)

/** «Configure Logcat formatting options»: пресеты, формат времени, колонки с шириной, поведение списка и размер буфера. */
@Composable
fun ConfigurePopup(options: ViewOptions, onDismiss: () -> Unit) {
  PopupContainer(onDismissRequest = onDismiss, horizontalAlignment = Alignment.Start, modifier = Modifier.width(380.dp)) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("View")
        OutlinedButton(onClick = { options.applyPreset(compact = false) }) { Text("Standard") }
        OutlinedButton(onClick = { options.applyPreset(compact = true) }) { Text("Compact") }
      }

      GroupHeader("Timestamp")
      CheckboxRow("Show timestamp", options.showTimestamp, { options.applyShowTimestamp(it) })
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Format")
        Dropdown(menuContent = {
          listOf("TIME" to "Time", "DATE_TIME" to "Date and time", "EPOCH" to "Epoch (ms)").forEach { (id, label) ->
            selectableItem(selected = options.timestampFormat == id, onClick = { options.applyTimestampFormat(id) }) { Text(label) }
          }
        }) { Text(when (options.timestampFormat) { "DATE_TIME" -> "Date and time"; "EPOCH" -> "Epoch (ms)"; else -> "Time" }) }
      }

      GroupHeader("Columns")
      CheckboxRow("Process ID", options.showPid, { options.applyShowPid(it) })
      CheckboxRow("Tag", options.showTag, { options.applyShowTag(it) })
      Stepper("Tag width", options.tagWidth, enabled = options.showTag) { options.applyTagWidth(it) }
      CheckboxRow("Hide repeated tags", options.hideRepeatedTags, { options.applyHideRepeatedTags(it) })
      CheckboxRow("Application ID", options.showPackage, { options.applyShowPackage(it) })
      Stepper("Application ID width", options.packageWidth, enabled = options.showPackage) { options.applyPackageWidth(it) }
      CheckboxRow("Level", options.showLevel, { options.applyShowLevel(it) })

      GroupHeader("Messages")
      CheckboxRow("Soft-wrap long lines", options.softWrap, { options.applySoftWrap(it) })
      CheckboxRow("Fold stack traces", options.foldStacks, { options.applyFoldStacks(it) })
      CheckboxRow("Auto-expand JSON and Intent", options.autoExpandJson, { options.applyAutoExpandJson(it) })
      CheckboxRow("Auto-expand long URLs (one parameter per line)", options.autoExpandUrls, { options.applyAutoExpandUrls(it) })

      GroupHeader("Buffer")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Keep last")
        Dropdown(menuContent = {
          BUFFER_SIZES.forEach { n -> selectableItem(selected = options.bufferLines == n, onClick = { options.applyBufferLines(n) }) { Text("%,d lines".format(n)) } }
        }) { Text("%,d lines".format(options.bufferLines)) }
      }
    }
  }
}

@Composable
private fun Stepper(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label)
    OutlinedButton(onClick = { onChange(value - 1) }, enabled = enabled) { Text("−") }
    Text(value.toString())
    OutlinedButton(onClick = { onChange(value + 1) }, enabled = enabled) { Text("+") }
  }
}
