// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package io.sdopenide.opencode

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

private const val NEW_CONFIG = "{\n  \"\$schema\": \"https://opencode.ai/config.json\"\n}\n"

/** `model` and `small_model` of one opencode.json. */
private data class Models(val model: String, val smallModel: String)

/**
 * Settings | Tools | OpenCode. The default version and the opencode.json of each version are IDE options;
 * the models are written into that opencode.json, so opencode started outside the IDE with it sees them too.
 */
internal class OpenCodeSettingsConfigurable(private val project: Project) : Configurable {
  private val versions = openCodeVersions().map { it.version }
  private val defaultVersion = ComboBox(versions.toTypedArray())
  private val configVersion = ComboBox(versions.toTypedArray())
  private val configPath = TextFieldWithBrowseButton()
  private val model = JBTextField()
  private val smallModel = JBTextField()
  private val fileStatus = JBLabel()
  private val fileText = JBTextArea(18, 80).apply {
    isEditable = false
    font = EditorFontType.getGlobalPlainFont()
  }

  /** opencode.json paths as edited here, by version. */
  private val paths = mutableMapOf<String, String>()
  /** Models as edited here and as on disk, by config file. */
  private val editedModels = mutableMapOf<Path, Models>()
  private val savedModels = mutableMapOf<Path, Models>()
  private var shownVersion: String? = null
  /** The file shown below, null while its models cannot be read. */
  private var shownFile: Path? = null
  private var updating = false

  override fun getDisplayName(): String = "OpenCode"

  override fun createComponent(): JComponent {
    configPath.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile().withTitle("opencode.json"))
    configVersion.addActionListener {
      if (!updating) {
        rememberModels()
        show(configVersion.selectedItem as String)
      }
    }
    configPath.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        val version = shownVersion ?: return
        if (updating) return
        rememberModels()
        paths[version] = configPath.text
        showFile()
      }
    })
    return panel {
      row("Default version:") {
        cell(defaultVersion).comment("Opened by the OpenCode button and \"+\", and run by <code>opencode</code> in Terminal")
      }
      group("Configuration") {
        row("Version:") { cell(configVersion) }
        row("opencode.json:") {
          cell(configPath).align(AlignX.FILL).comment(
            "Passed to this version as OPENCODE_CONFIG and applied on top of the global ~/.config/opencode/opencode.json; " +
            "leave empty to use the global file only.<br>opencode 2 tabs with their own file start <code>opencode --standalone</code>; " +
            "start it the same way after you exit opencode in such a tab.")
        }
        row("Default model:") { cell(model).align(AlignX.FILL).comment("<code>model</code> in the file, for example anthropic/claude-sonnet-4-5") }
        row("Small model:") { cell(smallModel).align(AlignX.FILL).comment("<code>small_model</code>, for titles and other light tasks") }
        row {
          cell(fileStatus)
          button("Open in Editor") { openInEditor() }
        }
        row { scrollCell(fileText).align(Align.FILL) }.resizableRow()
      }
    }
  }

  override fun reset() {
    paths.clear()
    editedModels.clear()
    savedModels.clear()
    versions.forEach { paths[it] = openCodeConfigSetting(it) }
    defaultVersion.selectedItem = defaultOpenCodeVersion()?.version
    show(shownVersion ?: defaultOpenCodeVersion()?.version ?: return)
  }

  private fun show(version: String) {
    shownVersion = version
    updating = true
    try {
      configVersion.selectedItem = version
      configPath.text = paths[version].orEmpty()
    }
    finally {
      updating = false
    }
    showFile()
  }

  private fun fileOf(version: String): Path = configFile(paths[version].orEmpty()) ?: globalOpenCodeConfig()

  private fun showFile() {
    val file = fileOf(shownVersion ?: return)
    val text = try {
      if (Files.isRegularFile(file)) Files.readString(file) else null
    }
    catch (e: IOException) {
      fileStatus.text = "$file: ${e.message}"
      fileText.text = ""
      setModelsEnabled(null, null)
      return
    }
    fileText.text = text.orEmpty()
    fileText.caretPosition = 0
    val saved = try {
      savedModels.getOrPut(file) { readModels(text ?: NEW_CONFIG) }
    }
    catch (e: IllegalArgumentException) {
      fileStatus.text = "$file: cannot read the models, edit the file itself (${e.message})"
      setModelsEnabled(null, null)
      return
    }
    fileStatus.text = if (text == null) "$file (does not exist yet, Apply creates it when a model is set)" else file.toString()
    setModelsEnabled(file, editedModels[file] ?: saved)
  }

  private fun setModelsEnabled(file: Path?, models: Models?) {
    shownFile = file
    model.isEnabled = file != null
    smallModel.isEnabled = file != null
    model.text = models?.model.orEmpty()
    smallModel.text = models?.smallModel.orEmpty()
  }

  private fun rememberModels() {
    shownFile?.let { editedModels[it] = Models(model.text.trim(), smallModel.text.trim()) }
  }

  private fun changedModels(): Map<Path, Models> {
    rememberModels()
    return editedModels.filter { (file, models) -> models != savedModels[file] }
  }

  override fun isModified(): Boolean =
    defaultVersion.selectedItem != defaultOpenCodeVersion()?.version ||
    versions.any { paths[it].orEmpty().trim() != openCodeConfigSetting(it) } ||
    changedModels().isNotEmpty()

  override fun apply() {
    (defaultVersion.selectedItem as String?)?.let { defaultOpenCodeVersionName = it }
    versions.forEach { setOpenCodeConfigSetting(it, paths[it].orEmpty()) }
    for ((file, models) in changedModels()) {
      try {
        var text = if (Files.exists(file)) Files.readString(file) else NEW_CONFIG
        text = Jsonc.setString(text, "model", models.model.ifEmpty { null })
        text = Jsonc.setString(text, "small_model", models.smallModel.ifEmpty { null })
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
      }
      catch (e: IOException) {
        throw ConfigurationException("$file: ${e.message}", "OpenCode")
      }
      catch (e: IllegalArgumentException) {
        throw ConfigurationException("$file: ${e.message}", "OpenCode")
      }
      LocalFileSystem.getInstance().refreshNioFiles(listOf(file))
    }
    reset()
  }

  private fun openInEditor() {
    val file = fileOf(shownVersion ?: return)
    if (!Files.exists(file)) {
      Files.createDirectories(file.parent)
      Files.writeString(file, NEW_CONFIG)
    }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)?.let { FileEditorManager.getInstance(project).openFile(it, true) }
  }
}

private fun readModels(text: String): Models =
  Models(Jsonc.getString(text, "model").orEmpty(), Jsonc.getString(text, "small_model").orEmpty())
