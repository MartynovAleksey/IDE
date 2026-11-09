// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path

// shortcut: the @ completion lists at most this many project files, enough for most projects; index by name when it is not
private const val MAX_FILES = 20000

/**
 * A chat tab: a page (chat/chat.html, chat.js) over `claude -p` in stream-json mode. The page speaks claude's protocol itself;
 * this side pipes claude's stdout lines to the page and the page's `stdin:` messages to claude, and does what needs the IDE:
 * opening files, diffs and links, copying, listing project files for @, the tab title and the theme.
 * With [resume] (a session file of [claudeSessions]) the tab shows that conversation and continues it.
 */
internal fun addChatSession(project: Project, toolWindow: ToolWindow, claude: Path, resume: ClaudeSession? = null) {
  val manager = toolWindow.contentManager
  val disposable = Disposer.newDisposable("Claude Code chat")
  val browser = JBCefBrowser()
  Disposer.register(disposable, browser)
  val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
  Disposer.register(disposable, query)
  val content = ContentFactory.getInstance().createContent(browser.component, resume?.title?.take(30) ?: "New chat", false)
  fun js(code: String) = browser.cefBrowser.executeJavaScript(code, "", 0)
  var process: Process? = null
  query.addHandler { message ->
    val arg = message.substringAfter(':')
    when (message.substringBefore(':')) {
      "stdin" -> process?.outputStream?.let { synchronized(it) { it.write((arg + "\n").toByteArray()); it.flush() } }
      "open" -> invokeLater { LocalFileSystem.getInstance().refreshAndFindFileByPath(arg)?.let { OpenFileDescriptor(project, it).navigate(true) } }
      "link" -> arg.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(BrowserUtil::browse)
      "copy" -> CopyPasteManager.getInstance().setContents(StringSelection(arg))
      "title" -> invokeLater { content.displayName = arg.take(30) }
      "diff" -> invokeLater { showDiff(project, arg) }
      "files" -> ReadAction.nonBlocking<String> { projectFiles(project) }.expireWith(disposable)
        .submit(ApplicationManager.getApplication()::executeOnPooledThread).onSuccess { js("onFiles($it)") }
    }
    null
  }
  browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
    override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
      // claude starts once the page can take its output
      if (!frame.isMain || process != null) return
      resume?.let { session -> historyLines(session.file).forEach { js("onClaude(${jsString(it)}, true)") } }
      val args = resume?.let { listOf("--resume", it.id) }.orEmpty()
      process = startClaude(project, claude, args) { line -> js("onClaude(${jsString(line)})") }
        .also { p -> Disposer.register(disposable) { p.destroy() } }
      js("onStarted()")
    }
  }, browser.cefBrowser)
  ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(LafManagerListener.TOPIC, LafManagerListener {
    js("document.documentElement.style.cssText = ${jsString(themeCss())}")
  })
  keepEscapeInChat(browser.component, disposable) { js("onEscape()") }
  browser.loadHTML(chatHtml(query))
  content.preferredFocusableComponent = browser.component
  content.setDisposer(disposable)
  manager.addContent(content)
  manager.setSelectedContent(content, true)
}

private fun startClaude(project: Project, claude: Path, args: List<String>, onLine: (String) -> Unit): Process {
  val command = GeneralCommandLine(claude.toString(), "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
                                   "--include-partial-messages", "--permission-prompt-tool", "stdio")
    .withParameters(args)
    .withWorkDirectory(project.basePath)
    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    .withEnvironment(ClaudeCodeSettings.env())
  if (claude == bundledClaude()) command.withEnvironment("DISABLE_AUTOUPDATER", "1")
  val process = command.createProcess()
  val app = ApplicationManager.getApplication()
  app.executeOnPooledThread { process.inputStream.bufferedReader().forEachLine(onLine); onLine("""{"type":"exit","code":${process.waitFor()}}""") }
  // not JSON: the page keeps it to show if claude exits
  app.executeOnPooledThread { process.errorStream.bufferedReader().forEachLine(onLine) }
  return process
}

/** The lines of a session file that may be messages for the page (it checks them), without subagents' own transcripts. */
private fun historyLines(file: Path): List<String> =
  Files.readAllLines(file).filter { ("\"type\":\"user\"" in it || "\"type\":\"assistant\"" in it) && "\"isSidechain\":true" !in it }

/** Project files relative to the project root, as a JS array literal, for @ completion. */
private fun projectFiles(project: Project): String {
  val base = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return "[]"
  val paths = ArrayList<String>()
  ProjectFileIndex.getInstance(project).iterateContent { file ->
    if (!file.isDirectory) VfsUtilCore.getRelativePath(file, base)?.let(paths::add)
    paths.size < MAX_FILES
  }
  return paths.joinToString(",", "[", "]") { jsString(it) }
}

/** `diff:{"path","old","new"}` from an Edit card: the replaced text against the new one, in the IDE diff viewer. */
private fun showDiff(project: Project, json: String) {
  val o = Json.parseToJsonElement(json).jsonObject
  fun field(name: String) = o[name]?.jsonPrimitive?.content.orEmpty()
  val path = field("path")
  val type = FileTypeManager.getInstance().getFileTypeByFileName(path.substringAfterLast('/'))
  val factory = DiffContentFactory.getInstance()
  DiffManager.getInstance().showDiff(project, SimpleDiffRequest(path, factory.create(project, field("old"), type), factory.create(project, field("new"), type), "Before", "After"))
}

private fun themeCss(): String {
  val font = JBFont.label()
  val editor = EditorColorsManager.getInstance().globalScheme
  return "--bg:${ColorUtil.toHtmlColor(UIUtil.getPanelBackground())};--fg:${ColorUtil.toHtmlColor(UIUtil.getLabelForeground())};" +
         "--font:'${font.family}',system-ui,sans-serif;--size:${font.size}px;" +
         "--mono:'${editor.editorFontName}',ui-monospace,monospace;--mono-size:${editor.editorFontSize}px;" +
         "color-scheme:${if (JBColor.isBright()) "light" else "dark"};"
}

private fun chatHtml(query: JBCefJSQuery): String {
  fun resource(name: String) = object {}.javaClass.getResource("/chat/$name")!!.readText()
  return resource("chat.html")
    .replace("/*THEME*/", themeCss())
    .replace("/*IDE*/", "var escapeFromIde = true; function ide(m) { ${query.inject("m")} }")
    .replace("/*SCRIPT*/", resource("chat.js"))
}

/** [s] as a JavaScript string literal. */
internal fun jsString(s: String): String = buildString {
  append('"')
  for (c in s) {
    when {
      c == '"' || c == '\\' -> append('\\').append(c)
      c < ' ' || c == ' ' || c == ' ' -> append("\\u%04x".format(c.code))
      else -> append(c)
    }
  }
  append('"')
}
