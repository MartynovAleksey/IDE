// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.opencode

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.shellStartupOptions
import java.nio.file.Files
import javax.swing.SwingConstants

/**
 * SD-OpenIDE: runs the opencode binary bundled into the terminal plugin (`bin/opencode`) in a terminal widget.
 */
internal class OpenCodeToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val binary = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.plugins.terminal"))?.pluginPath?.resolve("bin/opencode")
    val content = if (binary != null && Files.isExecutable(binary)) {
      val options = shellStartupOptions(project.basePath) { it.shellCommand(listOf(binary.toString())) }
      val widget = LocalTerminalDirectRunner.createTerminalRunner(project)
        .startShellTerminalWidget(toolWindow.disposable, options, true)
      ContentFactory.getInstance().createContent(widget.component, null, false).also {
        it.preferredFocusableComponent = widget.preferredFocusableComponent
      }
    }
    else {
      @Suppress("HardCodedStringLiteral")
      val label = JBLabel("opencode not found: $binary", SwingConstants.CENTER)
      ContentFactory.getInstance().createContent(label, null, false)
    }
    toolWindow.contentManager.addContent(content)
  }
}
