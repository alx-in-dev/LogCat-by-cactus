package com.cactus.logcactus.plugin.settings

import com.cactus.logcactus.plugin.LogcatSettings
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/** Settings | Tools | LogCactus: те же опции вида, что и в меню шестерёнки окна. */
class LogCactusConfigurable(private val project: Project) : BoundSearchableConfigurable("LogCactus", "com.cactus.logcactus.settings") {
  override fun createPanel(): DialogPanel {
    val s = LogcatSettings.getInstance(project).state
    return panel {
      group("Log format") {
        row { checkBox("Show timestamp").bindSelected(s::showTimestamp) }
        row { checkBox("Show process id").bindSelected(s::showPid) }
        row { checkBox("Show tag").bindSelected(s::showTag) }
        row { checkBox("Show application id").bindSelected(s::showPackage) }
        row { checkBox("Hide repeated tags").bindSelected(s::hideRepeatedTags) }
      }
      group("Behaviour") {
        row { checkBox("Soft-wrap long lines").bindSelected(s::softWrap) }
        row { checkBox("Fold stack traces").bindSelected(s::foldStacks) }
        row { checkBox("Auto-expand JSON and Intent").bindSelected(s::autoExpandJson) }
        row { checkBox("Auto-expand long URLs").bindSelected(s::autoExpandUrls) }
      }
    }
  }

  override fun apply() {
    super.apply()
    com.cactus.logcactus.plugin.ViewOptions.getInstance(project).syncFromSettings()
  }
}
