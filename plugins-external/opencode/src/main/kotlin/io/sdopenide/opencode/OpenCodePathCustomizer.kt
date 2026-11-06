// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.opencode

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Puts the default opencode version on PATH of local terminal sessions with its opencode.json in OPENCODE_CONFIG,
 * so `opencode -s <id>` works in any tab. An OpenCode tab passes the bin directory of its own version in [OPENCODE_BIN_DIR_ENV]
 * and sets OPENCODE_CONFIG itself.
 */
internal class OpenCodePathCustomizer : ShellExecOptionsCustomizer {
  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    val tabBinDir = shellExecOptions.envs[OPENCODE_BIN_DIR_ENV]?.let { Path.of(it) }
    if (tabBinDir != null) {
      if (Files.isRegularFile(tabBinDir.resolve(binaryName))) shellExecOptions.prependEntryToPATH(tabBinDir)
      return
    }
    val version = defaultOpenCodeVersion() ?: return
    shellExecOptions.prependEntryToPATH(version.binary.parent)
    if ("OPENCODE_CONFIG" !in shellExecOptions.envs) {
      openCodeConfigPath(version.version)?.let { shellExecOptions.setEnvironmentVariableToPath("OPENCODE_CONFIG", it) }
    }
  }
}
