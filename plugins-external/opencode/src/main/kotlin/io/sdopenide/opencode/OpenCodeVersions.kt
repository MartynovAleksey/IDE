// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.opencode

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal val binaryName: String get() = if (SystemInfo.isWindows) "opencode.exe" else "opencode"

private fun bundledBinDir(): Path? = PluginManagerCore.getPlugin(PluginId.getId("io.sdopenide.opencode"))?.pluginPath?.resolve("bin")

/** The plugin's own opencode (`bin/opencode[.exe]`); absent in a zip built without package_plugin.py, e.g. in `runIde`. */
internal fun openCodeBinaryPath(): Path? = bundledBinDir()?.resolve(binaryName)

/** A bundled opencode binary and its version. */
internal data class OpenCodeVersion(val version: @NlsSafe String, val binary: Path)

/** `bin/opencode[.exe]` (its version is in `bin/version.txt` from package_plugin.py), then each `bin/versions/<version>/`. */
internal fun openCodeVersions(): List<OpenCodeVersion> {
  val binDir = bundledBinDir() ?: return emptyList()
  val main = binDir.resolve(binaryName).takeIf { Files.isRegularFile(it) }?.let {
    val version = runCatching { Files.readString(binDir.resolve("version.txt")).trim() }.getOrNull()
    OpenCodeVersion(version?.ifEmpty { null } ?: "bundled", it)
  }
  val versionsDir = binDir.resolve("versions").takeIf { it.isDirectory() }
  val extra = versionsDir?.listDirectoryEntries().orEmpty()
    .map { OpenCodeVersion(it.name, it.resolve(binaryName)) }
    .filter { Files.isRegularFile(it.binary) }
    .sortedBy { it.version }
  return listOfNotNull(main) + extra
}

/** The version chosen in Settings | Tools | OpenCode, or the plugin's own opencode if none is chosen or it is gone. */
internal fun defaultOpenCodeVersion(): OpenCodeVersion? =
  openCodeVersions().let { versions -> versions.find { it.version == defaultOpenCodeVersionName } ?: versions.firstOrNull() }

internal var defaultOpenCodeVersionName: String
  get() = PropertiesComponent.getInstance().getValue("sd.opencode.defaultVersion", "")
  set(value) = PropertiesComponent.getInstance().setValue("sd.opencode.defaultVersion", value, "")

/** The config directory for [version] as typed in Settings, "" if none. */
internal fun openCodeConfigDirSetting(version: String): String = PropertiesComponent.getInstance().getValue("sd.opencode.configDir.$version", "")

internal fun setOpenCodeConfigDirSetting(version: String, path: String) {
  PropertiesComponent.getInstance().setValue("sd.opencode.configDir.$version", path.trim(), "")
}

/** The config directory given for [version], which tabs of that version pass in OPENCODE_CONFIG_DIR; null if none. */
internal fun openCodeConfigDir(version: String): Path? = configDir(openCodeConfigDirSetting(version))

internal fun configDir(setting: String): Path? {
  val path = setting.trim().ifEmpty { return null }
  return if (path == "~" || path.startsWith("~/")) Path.of(System.getProperty("user.home"), path.removePrefix("~").removePrefix("/")) else Path.of(path)
}

/** opencode's global config directory, which it always reads; OPENCODE_CONFIG_DIR is applied on top of it. */
internal fun globalOpenCodeConfigDir(): Path {
  val configHome = System.getenv("XDG_CONFIG_HOME")?.ifBlank { null }?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".config")
  return configHome.resolve("opencode")
}

/** The config file opencode reads in a config directory: opencode.jsonc, opencode.json or config.json. */
internal fun configFileIn(dir: Path): Path =
  listOf("opencode.jsonc", "opencode.json", "config.json").map { dir.resolve(it) }.firstOrNull { Files.isRegularFile(it) }
  ?: dir.resolve("opencode.json")
