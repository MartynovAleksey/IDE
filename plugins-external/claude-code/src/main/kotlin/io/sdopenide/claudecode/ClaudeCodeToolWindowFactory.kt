// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.text.DateFormatUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.jcef.JBCefApp
import java.nio.file.Files
import java.nio.file.Path

private const val TOOL_WINDOW_ID = "Claude Code"

/**
 * Tabs are chats (see [addChatSession]) or terminals: a local shell that starts `claude`, so the tab stays usable as a console after
 * claude exits. A chat needs JCEF and a claude binary; without them "+" opens a terminal, which says when claude is missing.
 */
internal class ClaudeCodeToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    setUpSessionTabs(project, toolWindow, listOf(NewChatAction(), HistoryAction(), NewTerminalAction())) { addChat(project, toolWindow) }
    keepEscapeInTerminals(toolWindow)
  }
}

private fun addChat(project: Project, toolWindow: ToolWindow) {
  val claude = claudeBinary()
  if (claude != null && JBCefApp.isSupported()) addChatSession(project, toolWindow, claude) else addTerminal(project, toolWindow)
}

private fun addTerminal(project: Project, toolWindow: ToolWindow) =
  addShellSession(project, toolWindow, "Terminal", claudeBinary(), "claude", ClaudeCodeSettings.env())

/** The bundled claude, else an installed one: PATH of the IDE, then the native installer's ~/.local/bin (the IDE started from Dock may lack it on PATH). */
private fun claudeBinary(): Path? {
  bundledClaude()?.let { return it }
  PathEnvironmentVariableUtil.findInPath(claudeExe)?.let { return it.toPath() }
  return Path.of(System.getProperty("user.home"), ".local", "bin", claudeExe).takeIf { Files.isRegularFile(it) }
}

private fun claudeToolWindow(e: AnActionEvent): ToolWindow? = e.project?.let { ToolWindowManager.getInstance(it).getToolWindow(TOOL_WINDOW_ID) }

private class NewChatAction : DumbAwareAction("New Chat", null, AllIcons.General.Add) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = claudeToolWindow(e) ?: return
    addChat(toolWindow.project, toolWindow)
  }
}

/** Earlier conversations of this project, newest first; picking one opens it in a chat tab that continues it. */
private class HistoryAction : DumbAwareAction("Chat History", null, AllIcons.Vcs.History) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = claudeToolWindow(e) ?: return
    val project = toolWindow.project
    val claude = claudeBinary()
    val sessions = project.basePath?.let { claudeSessions(it) }.orEmpty()
    val group = DefaultActionGroup()
    for (session in sessions) {
      @Suppress("HardCodedStringLiteral")
      group.add(object : DumbAwareAction("${session.title.take(60)}  ·  ${DateFormatUtil.formatPrettyDateTime(session.modified.toMillis())}") {
        override fun actionPerformed(e: AnActionEvent) {
          if (claude != null && JBCefApp.isSupported()) addChatSession(project, toolWindow, claude, session)
        }
      })
    }
    if (sessions.isEmpty()) group.add(object : DumbAwareAction("No earlier chats in this project") {
      override fun actionPerformed(e: AnActionEvent) = Unit
    })
    val popup = JBPopupFactory.getInstance().createActionGroupPopup("Chat History", group, e.dataContext, false, true, false, null, 20, null)
    val button = e.inputEvent?.component
    if (button != null) popup.showUnderneathOf(button) else popup.showInFocusCenter()
  }
}

private class NewTerminalAction : DumbAwareAction("New Terminal Session", null, AllIcons.Debugger.Console) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = claudeToolWindow(e) ?: return
    addTerminal(toolWindow.project, toolWindow)
  }
}
