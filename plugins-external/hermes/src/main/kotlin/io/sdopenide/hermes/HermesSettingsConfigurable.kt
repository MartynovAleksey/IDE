// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package io.sdopenide.hermes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

/** Settings | Tools | Hermes; values live in Hermes' own config and are written with `hermes config set`. */
internal class HermesSettingsConfigurable(private val project: Project) : Configurable {
  private val model = JBTextField()
  private val provider = JBTextField()
  private val baseUrl = JBTextField()
  private val interfaceMode = ComboBox(arrayOf("cli", "tui"))
  private val autoSetup = JBCheckBox("Run hermes setup in new Hermes tabs while no model is selected")
  private val status = JBLabel()
  private val hermesFields: List<JComponent> = listOf(model, provider, baseUrl, interfaceMode)
  private var loaded: HermesSettings? = null

  override fun getDisplayName(): String = "Hermes"

  override fun createComponent(): JComponent = panel {
    row { cell(status) }
    group("Model") {
      row("Default model:") { cell(model).align(AlignX.FILL).comment("For example anthropic/claude-sonnet-4.6") }
      row("Provider:") { cell(provider).align(AlignX.FILL).comment("auto, openrouter, anthropic, openai, nous, lmstudio, …") }
      row("Base URL:") { cell(baseUrl).align(AlignX.FILL).comment("Only for OpenAI-compatible endpoints; leave empty otherwise") }
    }
    group("Interface") {
      row("Interface:") { cell(interfaceMode) }
      row { cell(autoSetup) }
    }
    group("Wizards and Files") {
      row {
        button("Run hermes setup…") { openHermesSession(project, "hermes setup") }
        button("Select model…") { openHermesSession(project, "hermes model") }
      }
      row {
        button("Open config.yaml") { loaded?.configPath?.let { openInEditor(it) } }
        button("Open .env") { loaded?.envPath?.let { openInEditor(it) } }
      }
    }
  }

  override fun reset() {
    autoSetup.isSelected = hermesAutoSetup
    hermesFields.forEach { it.isEnabled = false }
    status.text = "Loading Hermes configuration…"
    ApplicationManager.getApplication().executeOnPooledThread {
      val settings = HermesCli.read()
      ApplicationManager.getApplication().invokeLater({ show(settings) }, ModalityState.any())
    }
  }

  private fun show(settings: HermesSettings?) {
    loaded = settings
    if (settings == null) {
      status.text = if (hermesLauncherPath()?.let(Files::isRegularFile) == true) "Could not read the Hermes configuration"
      else "This Hermes plugin was built without Hermes for this OS"
      return
    }
    model.text = settings.model
    provider.text = settings.provider
    baseUrl.text = settings.baseUrl
    interfaceMode.selectedItem = settings.interfaceMode
    hermesFields.forEach { it.isEnabled = true }
    status.text = settings.configPath
  }

  private fun edited(): List<Pair<String, String>> {
    val settings = loaded ?: return emptyList()
    return listOf(
      Triple("model.default", settings.model, model.text.trim()),
      Triple("model.provider", settings.provider, provider.text.trim()),
      Triple("model.base_url", settings.baseUrl, baseUrl.text.trim()),
      Triple("display.interface", settings.interfaceMode, interfaceMode.selectedItem as String),
    ).filter { (_, old, new) -> old != new }.map { (key, _, new) -> key to new }
  }

  override fun isModified(): Boolean = autoSetup.isSelected != hermesAutoSetup || edited().isNotEmpty()

  override fun apply() {
    hermesAutoSetup = autoSetup.isSelected
    val changes = edited()
    if (changes.isEmpty()) return
    val errors = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<String>, RuntimeException>({
      changes.mapNotNull { (key, value) ->
        val output = HermesCli.set(key, value)
        if (output != null && output.exitCode == 0) null else "$key: ${output?.stderr?.trim()?.lines()?.lastOrNull() ?: "Hermes is not available"}"
      }
    }, "Saving Hermes Settings", false, project)
    if (errors.isNotEmpty()) throw ConfigurationException(errors.joinToString("\n"), "Hermes")
    reset()
  }

  private fun openInEditor(path: String) {
    val file = Path.of(path)
    if (!Files.exists(file)) {
      Files.createDirectories(file.parent)
      Files.createFile(file)
    }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)?.let { FileEditorManager.getInstance(project).openFile(it, true) }
  }
}
