// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx

// SD-OpenIDE places a tool window with this id between Terminal and Services and keeps Escape in its terminals
private const val TOOL_WINDOW_ID = "Blackbird"

/** Env var with the bin directory of the blackbird version a tab runs; [BlackbirdPathCustomizer] puts it first on PATH. */
internal const val BLACKBIRD_BIN_DIR_ENV: String = "SD_BLACKBIRD_BIN_DIR"

/**
 * Each tab is a local shell that starts `blackbird`, so the tab stays usable as a console after blackbird exits.
 */
internal class BlackbirdToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    setUpSessionTabs(project, toolWindow, listOf(NewSessionAction(), NewSessionWithVersionAction())) { addSession(project, toolWindow) }
    (toolWindow as? ToolWindowEx)?.setAdditionalGearActions(DefaultActionGroup(OpenBlackbirdSettingsAction()))
    keepEscapeInTerminals(toolWindow)
  }
}

private fun addSession(project: Project, toolWindow: ToolWindow, version: BlackbirdVersion? = defaultBlackbirdVersion()) {
  if (version == null) {
    addShellSession(project, toolWindow, TOOL_WINDOW_ID, blackbirdBinaryPath(), "blackbird")
    return
  }
  val baseTitle = if (version == defaultBlackbirdVersion()) TOOL_WINDOW_ID else "$TOOL_WINDOW_ID ${version.version}"
  val config = sessionConfigDir(version.version)
  val env = buildMap {
    put(BLACKBIRD_BIN_DIR_ENV, version.binary.parent.toString())
    if (config != null) put("BLACKBIRD_CONFIG_DIR", config.toString())
  }
  addShellSession(project, toolWindow, baseTitle, version.binary, sessionCommand(version.version, opencodeCompatible(version.version), config), env)
}

private fun blackbirdToolWindow(e: AnActionEvent): ToolWindow? =
  e.project?.let { ToolWindowManager.getInstance(it).getToolWindow(TOOL_WINDOW_ID) }

private class OpenBlackbirdSettingsAction : DumbAwareAction("Blackbird Settings…", null, AllIcons.General.Settings) {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, BlackbirdSettingsConfigurable::class.java)
  }
}

private class NewSessionAction :DumbAwareAction("New Session", null, AllIcons.General.Add) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = blackbirdToolWindow(e) ?: return
    addSession(toolWindow.project, toolWindow)
  }
}

/** The chevron after "+": a popup with the bundled blackbird versions, like Terminal's shell list. */
private class NewSessionWithVersionAction : DumbAwareAction("New Session with blackbird Version", null, AllIcons.Toolbar.Expand) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = blackbirdToolWindow(e) ?: return
    val group = DefaultActionGroup()
    val default = defaultBlackbirdVersion()
    for (version in blackbirdVersions()) {
      val suffix = if (version == default) " (default)" else ""
      @Suppress("HardCodedStringLiteral")
      group.add(object : DumbAwareAction("blackbird ${version.version}$suffix") {
        override fun actionPerformed(e: AnActionEvent) = addSession(toolWindow.project, toolWindow, version)
      })
    }
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, e.dataContext, false, true, false, null, -1, null)
    val button = e.inputEvent?.component
    if (button != null) {
      popup.showUnderneathOf(button)
      PopupUtil.setPopupToggleComponent(popup, button)
    }
    else {
      popup.showInFocusCenter()
    }
  }
}
