// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Files
import java.nio.file.Path

internal val claudeExe: String get() = if (SystemInfo.isWindows) "claude.exe" else "claude"

/** The plugin's own claude (`bin/claude[.exe]` from package_plugin.py); absent in a zip from plain `buildPlugin`. */
internal fun bundledClaude(): Path? =
  PluginManagerCore.getPlugin(PluginId.getId("io.sdopenide.claudecode"))?.pluginPath?.resolve("bin")?.resolve(claudeExe)
    ?.takeIf { Files.isRegularFile(it) }

/**
 * Puts the bundled claude first on PATH of local terminals, so `claude` works in Claude Code tabs and in Terminal.
 * Its version is the plugin's, so claude's self-update is off: it would install another claude into ~/.local.
 */
internal class ClaudeCodePathCustomizer : ShellExecOptionsCustomizer {
  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    val claude = bundledClaude() ?: return
    shellExecOptions.prependEntryToPATH(claude.parent)
    shellExecOptions.setEnvironmentVariable("DISABLE_AUTOUPDATER", "1")
  }
}
