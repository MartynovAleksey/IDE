// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

/** A conversation claude saved for a project: `<config dir>/projects/<project path, non-alphanumerics as ->/<id>.jsonl`. */
internal data class ClaudeSession(val file: Path, val title: String, val modified: FileTime) {
  val id: String get() = file.nameWithoutExtension
}

/** The latest sessions of the project in [projectDir], newest first. */
internal fun claudeSessions(projectDir: String, limit: Int = 50): List<ClaudeSession> {
  val configDir = System.getenv("CLAUDE_CONFIG_DIR")?.ifBlank { null }?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".claude")
  val dir = configDir.resolve("projects").resolve(projectDir.replace(Regex("[^A-Za-z0-9]"), "-"))
  if (!dir.isDirectory()) return emptyList()
  return dir.listDirectoryEntries("*.jsonl").sortedByDescending { it.getLastModifiedTime() }.take(limit)
    .mapNotNull { file -> sessionTitle(file)?.let { ClaudeSession(file, it, file.getLastModifiedTime()) } }
}

/** A title the user gave, else the first prompt; null for a session without prompts. */
internal fun sessionTitle(file: Path): String? {
  var first: String? = null
  Files.newBufferedReader(file).useLines { lines ->
    for (line in lines) {
      if ("\"custom-title\"" in line) parse(line)?.get("customTitle")?.let { return (it as? JsonPrimitive)?.content }
      if (first == null && "\"type\":\"user\"" in line && "\"isMeta\":true" !in line) first = parse(line)?.let(::promptText)
    }
  }
  return first
}

private fun parse(line: String): JsonObject? = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()

/** The text the user typed in a `user` line; null for tool results and command wrappers (`<command-name>`, reminders). */
internal fun promptText(o: JsonObject): String? {
  if (o["type"]?.jsonPrimitive?.content != "user") return null
  val content = o["message"]?.jsonObject?.get("content") ?: return null
  val text = when (content) {
    is JsonPrimitive -> content.content
    is JsonArray -> content.mapNotNull { (it as? JsonObject)?.takeIf { b -> b["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.content }.firstOrNull()
    else -> null
  }
  return text?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("<") }?.lineSequence()?.first()
}
