// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.hermes

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

// SD-OpenIDE places a tool window with this id between OpenCode and Services and keeps Escape in its terminals
private const val HERMES_TOOL_WINDOW_ID = "Hermes"

/** The portable Hermes inside this plugin (`hermes/`); absent in a zip built without the payload and when running from sources. */
internal fun hermesHome(): Path? = PluginManagerCore.getPlugin(PluginId.getId("io.sdopenide.hermes"))?.pluginPath?.resolve("hermes")

internal fun hermesLauncherPath(): Path? = hermesHome()?.resolve("bin")?.resolve(if (SystemInfo.isWindows) "hermes.cmd" else "hermes")

/** Hermes Agent sessions, each a local shell in the project root that starts `hermes`. */
internal class HermesToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    (toolWindow as? ToolWindowEx)?.setAdditionalGearActions(DefaultActionGroup(OpenHermesSettingsAction()))
    setUpSessionTabs(project, toolWindow, listOf(NewHermesSessionAction())) { addHermesSession(project, toolWindow) }
    keepEscapeInTerminals(toolWindow)
  }
}

private val sessionsBeingStarted: MutableSet<ToolWindow> = ConcurrentHashMap.newKeySet()

/**
 * Starts `hermes`; with the opt-in auto-setup, `hermes setup` while no model is selected (Hermes itself skips its first-run
 * wizard when any provider credential is found, then refuses model-less sessions with `storage_unavailable`).
 */
private fun addHermesSession(project: Project, toolWindow: ToolWindow) {
  if (!hermesAutoSetup) {
    addShellSession(project, toolWindow, HERMES_TOOL_WINDOW_ID, hermesLauncherPath(), "hermes")
    return
  }
  if (!sessionsBeingStarted.add(toolWindow)) return // the shown-listener may fire while the config is still being read
  ApplicationManager.getApplication().executeOnPooledThread {
    val needsSetup = HermesCli.read()?.let { it.model.isBlank() } ?: false
    ApplicationManager.getApplication().invokeLater {
      sessionsBeingStarted.remove(toolWindow)
      if (!project.isDisposed) {
        addShellSession(project, toolWindow, HERMES_TOOL_WINDOW_ID, hermesLauncherPath(), if (needsSetup) "hermes setup" else "hermes")
      }
    }
  }
}

/** Opens a Hermes tab running [command], e.g. a setup wizard started from Settings | Tools | Hermes. */
internal fun openHermesSession(project: Project, command: String) {
  val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(HERMES_TOOL_WINDOW_ID) ?: return
  toolWindow.show {
    addShellSession(project, toolWindow, HERMES_TOOL_WINDOW_ID, hermesLauncherPath(), command)
  }
}

private class NewHermesSessionAction : DumbAwareAction("New Session", null, AllIcons.General.Add) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(HERMES_TOOL_WINDOW_ID) ?: return
    addHermesSession(project, toolWindow)
  }
}

private class OpenHermesSettingsAction : DumbAwareAction("Hermes Settings…", null, AllIcons.General.Settings) {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, HermesSettingsConfigurable::class.java)
  }
}
