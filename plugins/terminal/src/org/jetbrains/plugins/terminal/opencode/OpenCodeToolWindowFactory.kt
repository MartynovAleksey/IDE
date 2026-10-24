// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.opencode

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private const val TOOL_WINDOW_ID = "OpenCode"

/** Env var with the bin directory of the opencode version a tab runs; [OpenCodePathCustomizer] puts it first on PATH. */
internal const val OPENCODE_BIN_DIR_ENV: String = "SD_OPENCODE_BIN_DIR"

internal val binaryName: String get() = if (SystemInfo.isWindows) "opencode.exe" else "opencode"

private fun bundledBinDir(): Path? = terminalPluginDir()?.resolve("bin")

/** The default opencode bundled into the terminal plugin (`bin/opencode[.exe]`); may not exist, e.g. when running from sources. */
internal fun openCodeBinaryPath(): Path? = bundledBinDir()?.resolve(binaryName)

/** Additional versions bundled into `bin/versions/<version>/`, as (version, binary). */
private fun openCodeExtraVersions(): List<Pair<@NlsSafe String, Path>> {
  val versionsDir = bundledBinDir()?.resolve("versions")?.takeIf { it.isDirectory() } ?: return emptyList()
  return versionsDir.listDirectoryEntries()
    .map { it.name to it.resolve(binaryName) }
    .filter { Files.isRegularFile(it.second) }
    .sortedBy { it.first }
}

/**
 * SD-OpenIDE: each tab is a local shell that starts `opencode`, so the tab stays usable as a console after opencode exits.
 */
internal class OpenCodeToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    setUpSessionTabs(project, toolWindow, listOf(NewSessionAction(), NewSessionWithVersionAction())) { addSession(project, toolWindow) }
  }
}

/** @param version `null` for the default bundled opencode */
private fun addSession(project: Project, toolWindow: ToolWindow, version: String? = null, binary: Path? = openCodeBinaryPath()) {
  val baseTitle = if (version == null) TOOL_WINDOW_ID else "$TOOL_WINDOW_ID $version"
  val env = binary?.let { mapOf(OPENCODE_BIN_DIR_ENV to it.parent.toString()) } ?: emptyMap()
  addShellSession(project, toolWindow, baseTitle, binary, "opencode", env)
}

private fun openCodeToolWindow(e: AnActionEvent): ToolWindow? =
  e.project?.let { ToolWindowManager.getInstance(it).getToolWindow(TOOL_WINDOW_ID) }

private class NewSessionAction : DumbAwareAction("New Session", null, AllIcons.General.Add) {
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
    @Suppress("HardCodedStringLiteral")
    group.add(object : DumbAwareAction("opencode (default)") {
      override fun actionPerformed(e: AnActionEvent) = addSession(toolWindow.project, toolWindow)
    })
    for ((version, binary) in openCodeExtraVersions()) {
      @Suppress("HardCodedStringLiteral")
      group.add(object : DumbAwareAction("opencode $version") {
        override fun actionPerformed(e: AnActionEvent) = addSession(toolWindow.project, toolWindow, version, binary)
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
