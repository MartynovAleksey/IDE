// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.opencode

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Files
import java.nio.file.Path

/**
 * SD-OpenIDE: puts the bundled opencode on PATH of local terminal sessions, so `opencode -s <id>` works in any tab.
 * An OpenCode tab started with a specific version passes its bin directory in [OPENCODE_BIN_DIR_ENV].
 */
internal class OpenCodePathCustomizer : ShellExecOptionsCustomizer {
  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    val binDir = shellExecOptions.envs[OPENCODE_BIN_DIR_ENV]?.let { Path.of(it) } ?: openCodeBinaryPath()?.parent ?: return
    if (Files.isRegularFile(binDir.resolve(binaryName))) {
      shellExecOptions.prependEntryToPATH(binDir)
    }
  }
}
