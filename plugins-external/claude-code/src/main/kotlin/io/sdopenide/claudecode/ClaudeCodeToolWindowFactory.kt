// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Files
import java.nio.file.Path

private const val TOOL_WINDOW_ID = "Claude Code"

/** Each tab is a local shell that starts `claude`, so the tab stays usable as a console after claude exits. */
internal class ClaudeCodeToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    setUpSessionTabs(project, toolWindow, listOf(NewSessionAction())) { addSession(project, toolWindow) }
    keepEscapeInTerminals(toolWindow)
  }
}

private fun addSession(project: Project, toolWindow: ToolWindow) =
  addShellSession(project, toolWindow, TOOL_WINDOW_ID, claudeBinary(), "claude", ClaudeCodeSettings.env())

/** The installed Claude Code CLI: PATH of the IDE, then the native installer's ~/.local/bin (the IDE started from Dock may lack it on PATH). */
private fun claudeBinary(): Path? {
  val exe = if (SystemInfo.isWindows) "claude.exe" else "claude"
  PathEnvironmentVariableUtil.findInPath(exe)?.let { return it.toPath() }
  return Path.of(System.getProperty("user.home"), ".local", "bin", exe).takeIf { Files.isRegularFile(it) }
}

private class NewSessionAction : DumbAwareAction("New Session", null, AllIcons.General.Add) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = e.project?.let { ToolWindowManager.getInstance(it).getToolWindow(TOOL_WINDOW_ID) } ?: return
    addSession(toolWindow.project, toolWindow)
  }
}
