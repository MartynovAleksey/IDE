// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.opencode

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.shellStartupOptions
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingConstants

/** SD-OpenIDE: session tabs of agent tool windows (OpenCode; the Hermes plugin keeps its own copy). */
internal fun terminalPluginDir(): Path? =
  PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.plugins.terminal"))?.pluginPath

/**
 * Puts [tabActions] right after the tabs (as in Terminal), hides the tool window when its last tab closes,
 * opens a fresh session when it is shown again, and opens the first session now.
 */
internal fun setUpSessionTabs(project: Project, toolWindow: ToolWindow, tabActions: List<AnAction>, newSession: () -> Unit) {
  (toolWindow as? ToolWindowEx)?.setTabActions(*tabActions.toTypedArray())
  toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
    override fun contentRemoved(event: ContentManagerEvent) {
      if (toolWindow.contentManager.contentCount == 0) toolWindow.hide()
    }
  })
  project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun toolWindowShown(shownToolWindow: ToolWindow) {
      if (shownToolWindow === toolWindow && toolWindow.contentManager.contentCount == 0) newSession()
    }
  })
  newSession()
}

/**
 * Adds a tab with a local shell in the project root that runs [command] at once, so the tab stays a console after the agent exits.
 * Without [binary] the tab only says where the agent was expected.
 */
internal fun addShellSession(project: Project, toolWindow: ToolWindow, baseTitle: String, binary: Path?, command: String,
                             env: Map<String, String> = emptyMap()) {
  val manager = toolWindow.contentManager
  val title = if (manager.contentCount == 0) baseTitle else "$baseTitle (${manager.contentCount + 1})"
  val sessionDisposable = Disposer.newDisposable("$baseTitle session")
  val content = if (binary != null && Files.isRegularFile(binary)) {
    val options = shellStartupOptions(project.basePath) { it.envVariables(env) }
    val widget = LocalTerminalDirectRunner.createTerminalRunner(project).startShellTerminalWidget(sessionDisposable, options, true)
    widget.sendCommandToExecute(command) // resolved through PATH, see OpenCodePathCustomizer
    ContentFactory.getInstance().createContent(widget.component, title, false).also {
      it.preferredFocusableComponent = widget.preferredFocusableComponent
    }
  }
  else {
    @Suppress("HardCodedStringLiteral")
    val label = JBLabel("$command not found: $binary", SwingConstants.CENTER)
    ContentFactory.getInstance().createContent(label, title, false)
  }
  content.setDisposer(sessionDisposable)
  manager.addContent(content)
  manager.setSelectedContent(content, true)
}
