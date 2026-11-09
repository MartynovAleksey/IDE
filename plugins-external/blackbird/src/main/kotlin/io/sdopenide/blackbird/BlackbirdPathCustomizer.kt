// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Puts the default blackbird version on PATH of local terminal sessions with its config directory in BLACKBIRD_CONFIG_DIR and,
 * in the opencode-compatible mode, BLACKBIRD_OPENCODE_COMPATIBLE=1, so `blackbird -s <id>` works in any tab; also the interrupt hint, see [interruptHintEnv].
 * A Blackbird tab passes the bin directory of its own version in [BLACKBIRD_BIN_DIR_ENV] and sets the rest itself.
 */
internal class BlackbirdPathCustomizer : ShellExecOptionsCustomizer {
  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    val tabBinDir = shellExecOptions.envs[BLACKBIRD_BIN_DIR_ENV]?.let { Path.of(it) }
    if (tabBinDir != null) {
      if (Files.isRegularFile(tabBinDir.resolve(binaryName))) shellExecOptions.prependEntryToPATH(tabBinDir)
      return
    }
    val version = defaultBlackbirdVersion() ?: return
    shellExecOptions.prependEntryToPATH(version.binary.parent)
    val envs = shellExecOptions.envs
    if (opencodeCompatible(version.version) && "BLACKBIRD_OPENCODE_COMPATIBLE" !in envs) {
      shellExecOptions.setEnvironmentVariable("BLACKBIRD_OPENCODE_COMPATIBLE", "1")
    }
    if ("BLACKBIRD_CONFIG_DIR" !in envs) {
      sessionConfigDir(version.version)?.let { shellExecOptions.setEnvironmentVariableToPath("BLACKBIRD_CONFIG_DIR", it) }
    }
    interruptHintEnv(version.version).forEach { (name, value) -> if (name !in envs) shellExecOptions.setEnvironmentVariable(name, value) }
  }
}
