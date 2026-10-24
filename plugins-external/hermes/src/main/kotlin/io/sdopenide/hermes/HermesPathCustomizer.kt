// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.hermes

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Files

/** Puts the plugin's `hermes` on PATH of local terminal sessions, so Hermes tabs and any terminal can run it. */
internal class HermesPathCustomizer : ShellExecOptionsCustomizer {
  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    hermesLauncherPath()?.takeIf { Files.isRegularFile(it) }?.let { shellExecOptions.prependEntryToPATH(it.parent) }
  }
}
