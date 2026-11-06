// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

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

internal val binaryName: String get() = if (SystemInfo.isWindows) "blackbird.exe" else "blackbird"

private fun bundledBinDir(): Path? = PluginManagerCore.getPlugin(PluginId.getId("io.sdopenide.blackbird"))?.pluginPath?.resolve("bin")

/** The plugin's own blackbird (`bin/blackbird[.exe]`); absent in a zip built without package_plugin.py, e.g. in `runIde`. */
internal fun blackbirdBinaryPath(): Path? = bundledBinDir()?.resolve(binaryName)

/** A bundled blackbird binary and its version. */
internal data class BlackbirdVersion(val version: @NlsSafe String, val binary: Path)

/** `bin/blackbird[.exe]` (its version is in `bin/version.txt` from package_plugin.py), then each `bin/versions/<version>/`. */
internal fun blackbirdVersions(): List<BlackbirdVersion> {
  val binDir = bundledBinDir() ?: return emptyList()
  val main = binDir.resolve(binaryName).takeIf { Files.isRegularFile(it) }?.let {
    val version = runCatching { Files.readString(binDir.resolve("version.txt")).trim() }.getOrNull()
    BlackbirdVersion(version?.ifEmpty { null } ?: "bundled", it)
  }
  val versionsDir = binDir.resolve("versions").takeIf { it.isDirectory() }
  val extra = versionsDir?.listDirectoryEntries().orEmpty()
    .map { BlackbirdVersion(it.name, it.resolve(binaryName)) }
    .filter { Files.isRegularFile(it.binary) }
    .sortedBy { it.version }
  return listOfNotNull(main) + extra
}

/** The version chosen in Settings | Tools | Blackbird, or the plugin's own blackbird if none is chosen or it is gone. */
internal fun defaultBlackbirdVersion(): BlackbirdVersion? =
  blackbirdVersions().let { versions -> versions.find { it.version == defaultBlackbirdVersionName } ?: versions.firstOrNull() }

internal var defaultBlackbirdVersionName: String
  get() = PropertiesComponent.getInstance().getValue("sd.blackbird.defaultVersion", "")
  set(value) = PropertiesComponent.getInstance().setValue("sd.blackbird.defaultVersion", value, "")

/** The config directory for [version] as typed in Settings, "" if none. */
internal fun blackbirdConfigDirSetting(version: String): String = PropertiesComponent.getInstance().getValue("sd.blackbird.configDir.$version", "")

internal fun setBlackbirdConfigDirSetting(version: String, path: String) {
  PropertiesComponent.getInstance().setValue("sd.blackbird.configDir.$version", path.trim(), "")
}

/** The config directory given for [version], which tabs of that version pass in BLACKBIRD_CONFIG_DIR; null if none. */
internal fun blackbirdConfigDir(version: String): Path? = configDir(blackbirdConfigDirSetting(version))

internal fun configDir(setting: String): Path? {
  val path = setting.trim().ifEmpty { return null }
  return if (path == "~" || path.startsWith("~/")) Path.of(System.getProperty("user.home"), path.removePrefix("~").removePrefix("/")) else Path.of(path)
}

/** Whether sessions of [version] start in the opencode-compatible mode, on opencode's files and providers; on unless turned off. */
internal fun opencodeCompatible(version: String): Boolean = PropertiesComponent.getInstance().getBoolean("sd.blackbird.compat.$version", true)

internal fun setOpencodeCompatible(version: String, value: Boolean) {
  PropertiesComponent.getInstance().setValue("sd.blackbird.compat.$version", value, true)
}

/** opencode's config directory for [version] in the opencode-compatible mode as typed in Settings, "" if none. */
internal fun opencodeConfigDirSetting(version: String): String = PropertiesComponent.getInstance().getValue("sd.blackbird.opencodeDir.$version", "")

internal fun setOpencodeConfigDirSetting(version: String, path: String) {
  PropertiesComponent.getInstance().setValue("sd.blackbird.opencodeDir.$version", path.trim(), "")
}

/**
 * The directory sessions of [version] get in BLACKBIRD_CONFIG_DIR, null if none: opencode's one in the opencode-compatible mode,
 * where blackbird reads BLACKBIRD_CONFIG_DIR as OPENCODE_CONFIG_DIR just like `--opencode-compatible=<dir>` does.
 */
internal fun sessionConfigDir(version: String): Path? =
  if (opencodeCompatible(version)) configDir(opencodeConfigDirSetting(version)) else blackbirdConfigDir(version)

/** The command a session of [version] runs, given the directory it gets in BLACKBIRD_CONFIG_DIR. */
internal fun sessionCommand(version: String, compatible: Boolean, configDir: Path?): String = buildString {
  append("blackbird")
  if (compatible) append(" --opencode-compatible")
  // blackbird 2 runs sessions in a shared background service that does not see the tab's BLACKBIRD_CONFIG_DIR; --standalone gives the tab its own
  if (configDir != null && (version.substringBefore('.').toIntOrNull() ?: 0) >= 2) append(" --standalone")
}

/** The global config directory blackbird always reads (opencode's in the opencode-compatible mode); BLACKBIRD_CONFIG_DIR is applied on top of it. */
internal fun globalConfigDir(compatible: Boolean): Path {
  val configHome = System.getenv("XDG_CONFIG_HOME")?.ifBlank { null }?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".config")
  return configHome.resolve(if (compatible) "opencode" else "blackbird")
}

/** The config file read in a config directory: blackbird.jsonc, blackbird.json or config.json (opencode.* in the opencode-compatible mode). */
internal fun configFileIn(dir: Path, compatible: Boolean): Path {
  val name = if (compatible) "opencode" else "blackbird"
  return listOf("$name.jsonc", "$name.json", "config.json").map { dir.resolve(it) }.firstOrNull { Files.isRegularFile(it) }
         ?: dir.resolve("$name.json")
}
