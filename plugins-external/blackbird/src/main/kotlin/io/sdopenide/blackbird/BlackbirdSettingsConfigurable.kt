// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package io.sdopenide.blackbird

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
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

/** `model` and `small_model` of one blackbird.json. */
private data class Models(val model: String, val smallModel: String)

/**
 * Settings | Tools | Blackbird. The default version and, per version, the config directory and the opencode-compatible mode are
 * IDE options; the models are written into the config file of the directory the version uses, so blackbird started outside
 * the IDE with it sees them too.
 */
internal class BlackbirdSettingsConfigurable(private val project: Project) : Configurable {
  private val versions = blackbirdVersions().map { it.version }
  private val defaultVersion = ComboBox(versions.toTypedArray())
  private val configVersion = ComboBox(versions.toTypedArray())
  private val configDirField = TextFieldWithBrowseButton()
  private val compatible = JBCheckBox("opencode-compatible mode")
  private val opencodeDirField = TextFieldWithBrowseButton()
  private val model = JBTextField()
  private val smallModel = JBTextField()
  private val fileStatus = JBLabel()
  private val fileText = JBTextArea(18, 80).apply {
    isEditable = false
    font = EditorFontType.getGlobalPlainFont()
  }
  private val importPath = TextFieldWithBrowseButton()

  /** Config directories, opencode-compatible modes and opencode config directories as edited here, by version. */
  private val paths = mutableMapOf<String, String>()
  private val compatibleModes = mutableMapOf<String, Boolean>()
  private val opencodeDirs = mutableMapOf<String, String>()
  /** Models as edited here and as on disk, by config file. */
  private val editedModels = mutableMapOf<Path, Models>()
  private val savedModels = mutableMapOf<Path, Models>()
  private var shownVersion: String? = null
  /** The file shown below, null while its models cannot be read. */
  private var shownFile: Path? = null
  private var updating = false

  override fun getDisplayName(): String = "Blackbird"

  override fun createComponent(): JComponent {
    configDirField.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir().withTitle("blackbird Config Directory"))
    opencodeDirField.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir().withTitle("opencode Config Directory"))
    importPath.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFileOrDir().withTitle("opencode File or Directory to Import"))
    configVersion.addActionListener {
      if (!updating) {
        rememberModels()
        show(configVersion.selectedItem as String)
      }
    }
    onEdit(configDirField) { paths[it] = configDirField.text }
    onEdit(opencodeDirField) { opencodeDirs[it] = opencodeDirField.text }
    compatible.addActionListener {
      val version = shownVersion ?: return@addActionListener
      if (updating) return@addActionListener
      rememberModels()
      compatibleModes[version] = compatible.isSelected
      updateDirFields()
      showFile()
    }
    return panel {
      row("Default version:") {
        cell(defaultVersion).comment("Opened by the Blackbird button and \"+\", and run by <code>blackbird</code> in Terminal")
      }
      group("Configuration") {
        row("Version:") { cell(configVersion) }
        row("Config directory:") {
          cell(configDirField).align(AlignX.FILL).comment(
            "blackbird.json, agent/, command/, plugin/ and the like for this version, passed as BLACKBIRD_CONFIG_DIR and applied " +
            "on top of the global ~/.config/blackbird; leave empty to use the global directory only.<br>" +
            "blackbird 2 tabs with their own directory start <code>blackbird --standalone</code>; " +
            "start it the same way after you exit blackbird in such a tab.")
        }
        row {
          cell(compatible).comment(
            "Starts <code>blackbird --opencode-compatible</code>: blackbird works on opencode's config, sessions, keys and background " +
            "service, with its built-in providers, even while opencode itself runs on them.")
        }
        row("opencode config directory:") {
          cell(opencodeDirField).align(AlignX.FILL).comment(
            "Passed as BLACKBIRD_CONFIG_DIR, the same as <code>--opencode-compatible=&lt;dir&gt;</code>; " +
            "leave empty to use the global ~/.config/opencode only.")
        }
        row("Default model:") { cell(model).align(AlignX.FILL).comment("<code>model</code> in the file, for example anthropic/claude-sonnet-4-5") }
        row("Small model:") { cell(smallModel).align(AlignX.FILL).comment("<code>small_model</code>, for titles and other light tasks") }
        row {
          cell(fileStatus)
          button("Open in Editor") { openInEditor() }
          button(RevealFileAction.getActionName()) { RevealFileAction.openDirectory(dirOf(shownVersion ?: return@button)) }
        }
        row { scrollCell(fileText).align(Align.FILL) }.resizableRow()
      }
      group("Import from opencode") {
        row("opencode path:") {
          cell(importPath).align(AlignX.FILL).comment(
            "Runs <code>blackbird --opencode-import [path]</code> of the version above in the project directory. " +
            "Empty: opencode's whole global config, data and state go to blackbird's global directories (~/.config/blackbird " +
            "and the like), not to the config directory above. A file or directory is imported next to itself, " +
            "with opencode renamed to blackbird. Overwritten files are kept as .bak.")
        }
        row { button("Import from opencode") { importFromOpencode() } }
      }
    }
  }

  private fun onEdit(field: TextFieldWithBrowseButton, store: (version: String) -> Unit) {
    field.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        val version = shownVersion ?: return
        if (updating) return
        rememberModels()
        store(version)
        showFile()
      }
    })
  }

  override fun reset() {
    paths.clear()
    compatibleModes.clear()
    opencodeDirs.clear()
    editedModels.clear()
    savedModels.clear()
    for (version in versions) {
      paths[version] = blackbirdConfigDirSetting(version)
      compatibleModes[version] = opencodeCompatible(version)
      opencodeDirs[version] = opencodeConfigDirSetting(version)
    }
    defaultVersion.selectedItem = defaultBlackbirdVersion()?.version
    show(shownVersion ?: defaultBlackbirdVersion()?.version ?: return)
  }

  private fun show(version: String) {
    shownVersion = version
    updating = true
    try {
      configVersion.selectedItem = version
      configDirField.text = paths[version].orEmpty()
      compatible.isSelected = compatibleModes[version] == true
      opencodeDirField.text = opencodeDirs[version].orEmpty()
    }
    finally {
      updating = false
    }
    updateDirFields()
    showFile()
  }

  private fun updateDirFields() {
    configDirField.isEnabled = !compatible.isSelected
    opencodeDirField.isEnabled = compatible.isSelected
  }

  private fun isCompatible(version: String): Boolean = compatibleModes[version] == true

  /** The config directory [version] uses: opencode's in the opencode-compatible mode. */
  private fun dirOf(version: String): Path {
    val compatible = isCompatible(version)
    return configDir(if (compatible) opencodeDirs[version].orEmpty() else paths[version].orEmpty()) ?: globalConfigDir(compatible)
  }

  private fun fileOf(version: String): Path = configFileIn(dirOf(version), isCompatible(version))

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
    defaultVersion.selectedItem != defaultBlackbirdVersion()?.version ||
    versions.any {
      paths[it].orEmpty().trim() != blackbirdConfigDirSetting(it) ||
      isCompatible(it) != opencodeCompatible(it) ||
      opencodeDirs[it].orEmpty().trim() != opencodeConfigDirSetting(it)
    } ||
    changedModels().isNotEmpty()

  override fun apply() {
    (defaultVersion.selectedItem as String?)?.let { defaultBlackbirdVersionName = it }
    for (version in versions) {
      setBlackbirdConfigDirSetting(version, paths[version].orEmpty())
      setOpencodeCompatible(version, isCompatible(version))
      setOpencodeConfigDirSetting(version, opencodeDirs[version].orEmpty())
    }
    for ((file, models) in changedModels()) {
      try {
        var text = if (Files.exists(file)) Files.readString(file) else NEW_CONFIG
        text = Jsonc.setString(text, "model", models.model.ifEmpty { null })
        text = Jsonc.setString(text, "small_model", models.smallModel.ifEmpty { null })
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
      }
      catch (e: IOException) {
        throw ConfigurationException("$file: ${e.message}", "Blackbird")
      }
      catch (e: IllegalArgumentException) {
        throw ConfigurationException("$file: ${e.message}", "Blackbird")
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

  /** `blackbird --opencode-import [path]` of the shown version; blackbird prints what it copied, backed up and skipped. */
  private fun importFromOpencode() {
    val version = shownVersion ?: return
    val binary = blackbirdVersions().find { it.version == version }?.binary ?: return
    val title = "Import from opencode with blackbird $version"
    val command = GeneralCommandLine(binary.toString(), "--opencode-import").withWorkingDirectory(project.basePath?.let { Path.of(it) })
    configDir(importPath.text)?.let { command.addParameter(it.toString()) }
    val output = try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously<ProcessOutput, ExecutionException>(
        { CapturingProcessHandler(command).runProcess() }, title, false, project)
    }
    catch (e: ExecutionException) {
      Messages.showErrorDialog(importPath, e.message, title)
      return
    }
    if (output.exitCode == 0) Messages.showInfoMessage(importPath, output.stdout.ifBlank { "Done" }, title)
    else Messages.showErrorDialog(importPath, output.stderr.ifBlank { output.stdout }.ifBlank { "Exit code ${output.exitCode}" }, title)
    // the import may have created or replaced the config file shown above
    fileOf(version).let { savedModels.remove(it); editedModels.remove(it) }
    showFile()
  }
}

private fun readModels(text: String): Models =
  Models(Jsonc.getString(text, "model").orEmpty(), Jsonc.getString(text, "small_model").orEmpty())
