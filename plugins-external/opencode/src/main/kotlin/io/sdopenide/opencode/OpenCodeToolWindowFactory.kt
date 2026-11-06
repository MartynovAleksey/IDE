// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.opencode

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
private const val TOOL_WINDOW_ID = "OpenCode"

/** Env var with the bin directory of the opencode version a tab runs; [OpenCodePathCustomizer] puts it first on PATH. */
internal const val OPENCODE_BIN_DIR_ENV: String = "SD_OPENCODE_BIN_DIR"

/**
 * Each tab is a local shell that starts `opencode`, so the tab stays usable as a console after opencode exits.
 */
internal class OpenCodeToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    setUpSessionTabs(project, toolWindow, listOf(NewSessionAction(), NewSessionWithVersionAction())) { addSession(project, toolWindow) }
    (toolWindow as? ToolWindowEx)?.setAdditionalGearActions(DefaultActionGroup(OpenOpenCodeSettingsAction()))
    keepEscapeInTerminals(toolWindow)
  }
}

private fun addSession(project: Project, toolWindow: ToolWindow, version: OpenCodeVersion? = defaultOpenCodeVersion()) {
  if (version == null) {
    addShellSession(project, toolWindow, TOOL_WINDOW_ID, openCodeBinaryPath(), "opencode")
    return
  }
  val baseTitle = if (version == defaultOpenCodeVersion()) TOOL_WINDOW_ID else "$TOOL_WINDOW_ID ${version.version}"
  val config = openCodeConfigPath(version.version)
  val env = buildMap {
    put(OPENCODE_BIN_DIR_ENV, version.binary.parent.toString())
    if (config != null) put("OPENCODE_CONFIG", config.toString())
  }
  // opencode 2 runs sessions in a shared background service that does not see the tab's OPENCODE_CONFIG; --standalone gives the tab its own
  val standalone = config != null && (version.version.substringBefore('.').toIntOrNull() ?: 0) >= 2
  addShellSession(project, toolWindow, baseTitle, version.binary, if (standalone) "opencode --standalone" else "opencode", env)
}

private fun openCodeToolWindow(e: AnActionEvent): ToolWindow? =
  e.project?.let { ToolWindowManager.getInstance(it).getToolWindow(TOOL_WINDOW_ID) }

private class OpenOpenCodeSettingsAction : DumbAwareAction("OpenCode Settings…", null, AllIcons.General.Settings) {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, OpenCodeSettingsConfigurable::class.java)
  }
}

private class NewSessionAction :DumbAwareAction("New Session", null, AllIcons.General.Add) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = openCodeToolWindow(e) ?: return
    addSession(toolWindow.project, toolWindow)
  }
}

/** The chevron after "+": a popup with the bundled opencode versions, like Terminal's shell list. */
private class NewSessionWithVersionAction : DumbAwareAction("New Session with opencode Version", null, AllIcons.Toolbar.Expand) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = openCodeToolWindow(e) ?: return
    val group = DefaultActionGroup()
    val default = defaultOpenCodeVersion()
    for (version in openCodeVersions()) {
      val suffix = if (version == default) " (default)" else ""
      @Suppress("HardCodedStringLiteral")
      group.add(object : DumbAwareAction("opencode ${version.version}$suffix") {
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
