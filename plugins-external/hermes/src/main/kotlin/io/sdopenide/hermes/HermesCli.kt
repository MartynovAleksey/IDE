// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.hermes

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Hermes configuration values the IDE shows; empty strings for unset keys. */
internal data class HermesSettings(
  val configPath: String,
  val envPath: String,
  val model: String,
  val provider: String,
  val baseUrl: String,
  val interfaceMode: String,
)

/** IDE-side option, off by default: start `hermes setup` instead of `hermes` in new tabs while no model is selected. */
internal var hermesAutoSetup: Boolean
  get() = PropertiesComponent.getInstance().getBoolean("sd.hermes.autoSetup", false)
  set(value) = PropertiesComponent.getInstance().setValue("sd.hermes.autoSetup", value, false)

/**
 * Talks to the plugin's portable Hermes through its own Python, so Hermes keeps owning config paths
 * (HERMES_HOME, profiles, Windows) and the YAML format. Calls block; run them off the EDT.
 */
internal object HermesCli {
  // raw user values (not load_config(), which fills in defaults), one "key<TAB>value" line each
  private val READ_SCRIPT = """
    from hermes_cli.config import get_config_path, get_env_path
    import hermes_yaml
    p = get_config_path()
    c = (hermes_yaml.safe_load(p.read_text(encoding="utf-8-sig")) if p.exists() else None) or {}
    m = c.get("model") or {}
    m = {"default": m} if isinstance(m, str) else m
    d = c.get("display") or {}
    for k, v in (("configPath", p), ("envPath", get_env_path()), ("model", m.get("default")),
                 ("provider", m.get("provider")), ("baseUrl", m.get("base_url")), ("interface", d.get("interface"))):
        print(k + "\t" + ("" if v is None else str(v)))
  """.trimIndent()

  private fun commandLine(vararg args: String): GeneralCommandLine? {
    val home = hermesHome()?.takeIf { Files.isDirectory(it) } ?: return null
    val python = if (SystemInfo.isWindows) home.resolve("python/python.exe") else home.resolve("python/bin/python3.14") // the payload has no unversioned link
    return GeneralCommandLine(python.toString(), *args)
      .withEnvironment(mapOf(
        "PYTHONPATH" to "${home.resolve("app")}${File.pathSeparator}${home.resolve(if (SystemInfo.isWindows) "venv/Lib/site-packages" else "venv/lib/python3.14/site-packages")}",
        "PYTHONNOUSERSITE" to "1",
        "PYTHONIOENCODING" to "utf-8",
      ))
      .withCharset(StandardCharsets.UTF_8)
  }

  /** `null` when Hermes is not bundled or its configuration cannot be read. */
  fun read(): HermesSettings? {
    val output = ExecUtil.execAndGetOutput(commandLine("-c", READ_SCRIPT) ?: return null, 60_000)
    if (output.exitCode != 0 || output.isTimeout) return null
    val values = output.stdoutLines.mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    return HermesSettings(
      configPath = values["configPath"] ?: return null,
      envPath = values["envPath"].orEmpty(),
      model = values["model"].orEmpty(),
      provider = values["provider"].orEmpty(),
      baseUrl = values["baseUrl"].orEmpty(),
      interfaceMode = values["interface"].orEmpty().ifEmpty { "cli" },
    )
  }

  /** `hermes config set <key> <value>`, or `config unset <key>` for an empty value. */
  fun set(key: String, value: String): ProcessOutput? {
    val args = if (value.isEmpty()) arrayOf("-m", "hermes_cli.main", "config", "unset", key)
    else arrayOf("-m", "hermes_cli.main", "config", "set", key, value)
    return ExecUtil.execAndGetOutput(commandLine(*args) ?: return null, 60_000)
  }
}
