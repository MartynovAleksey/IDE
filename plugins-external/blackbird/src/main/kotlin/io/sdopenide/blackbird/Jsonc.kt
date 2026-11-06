// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

/**
 * Reads and rewrites top-level string members of a JSONC object (blackbird.json allows comments and trailing commas),
 * leaving the rest of the text, comments included, as it is. Malformed text throws [IllegalArgumentException].
 */
internal object Jsonc {
  /** The top-level string [key], or null if there is none; throws if its value is not a string. */
  fun getString(text: String, key: String): String? {
    val member = parse(text).members.lastOrNull { it.key == key } ?: return null
    require(text[member.valueStart] == '"') { "\"$key\" is not a string" }
    return Scanner(text, member.valueStart).string()
  }

  /** [text] with the top-level [key] set to [value], or removed when [value] is null. */
  fun setString(text: String, key: String, value: String?): String {
    val obj = parse(text)
    val member = obj.members.lastOrNull { it.key == key }
    if (member != null) {
      return if (value != null) text.replaceRange(member.valueStart, member.valueEnd, quote(value)) else remove(text, obj, member)
    }
    if (value == null) return text
    val entry = "${quote(key)}: ${quote(value)}"
    val last = obj.members.lastOrNull()
    if (last == null) {
      return if (text.substring(obj.open + 1, obj.close).isBlank()) text.replaceRange(obj.open + 1, obj.close, "\n  $entry\n")
      else text.replaceRange(obj.open + 1, obj.open + 1, "\n  $entry")
    }
    val separator = indentBefore(text, last.start)?.let { "\n$it" } ?: " "
    return if (last.comma >= 0) text.replaceRange(last.comma + 1, last.comma + 1, "$separator$entry,")
    else text.replaceRange(last.valueEnd, last.valueEnd, ",$separator$entry")
  }

  private fun remove(text: String, obj: JsonObject, member: Member): String {
    val indent = indentBefore(text, member.start)
    val start = member.start - (indent?.length ?: 0)
    if (member.comma >= 0) {
      var end = member.comma + 1
      while (end < text.length && (text[end] == ' ' || text[end] == '\t')) end++
      if (indent != null) end += if (text.startsWith("\r\n", end)) 2 else if (text.startsWith("\n", end)) 1 else 0
      return text.removeRange(start, end)
    }
    val previous = obj.members.getOrNull(obj.members.indexOf(member) - 1)
    return if (previous != null) text.removeRange(previous.comma, member.valueEnd) else text.removeRange(start, member.valueEnd)
  }

  private fun parse(text: String): JsonObject {
    val scanner = Scanner(text)
    scanner.skipTrivia()
    val open = scanner.i
    scanner.expect('{')
    val members = mutableListOf<Member>()
    while (true) {
      scanner.skipTrivia()
      if (scanner.peek() == '}') break
      val start = scanner.i
      val key = scanner.string()
      scanner.skipTrivia()
      scanner.expect(':')
      scanner.skipTrivia()
      val valueStart = scanner.i
      scanner.skipValue()
      val valueEnd = scanner.i
      scanner.skipTrivia()
      val comma = if (scanner.peek() == ',') scanner.i++ else -1
      members += Member(key, start, valueStart, valueEnd, comma)
      if (comma < 0) {
        if (scanner.peek() != '}') scanner.fail()
        break
      }
    }
    return JsonObject(open, scanner.i, members)
  }

  /** The indentation before [offset] if only spaces and tabs precede it on its line, else null. */
  private fun indentBefore(text: String, offset: Int): String? {
    var i = offset
    while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
    return if (i == 0 || text[i - 1] == '\n') text.substring(i, offset) else null
  }

  private fun quote(value: String): String = buildString {
    append('"')
    for (c in value) {
      when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
      }
    }
    append('"')
  }
}

/** A top-level member from its key's opening quote; [comma] is the offset of the comma after it, or -1. */
private class Member(val key: String, val start: Int, val valueStart: Int, val valueEnd: Int, val comma: Int)

private class JsonObject(val open: Int, val close: Int, val members: List<Member>)

private class Scanner(private val s: String, var i: Int = 0) {
  fun fail(): Nothing = throw IllegalArgumentException("Not a JSON object: unexpected text at offset $i")

  fun peek(): Char = if (i < s.length) s[i] else fail()

  fun expect(c: Char) {
    if (peek() != c) fail()
    i++
  }

  fun skipTrivia() {
    while (i < s.length) {
      when {
        s[i].isWhitespace() -> i++
        s.startsWith("//", i) -> i = s.indexOf('\n', i).let { if (it < 0) s.length else it + 1 }
        s.startsWith("/*", i) -> i = s.indexOf("*/", i + 2).let { if (it < 0) fail() else it + 2 }
        else -> return
      }
    }
  }

  fun string(): String {
    expect('"')
    val result = StringBuilder()
    while (true) {
      val c = peek()
      i++
      when (c) {
        '"' -> return result.toString()
        '\\' -> {
          val escaped = peek()
          i++
          when (escaped) {
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'b' -> result.append('\b')
            'f' -> result.append('\u000c')
            'u' -> {
              if (i + 4 > s.length) fail()
              result.append(s.substring(i, i + 4).toInt(16).toChar())
              i += 4
            }
            else -> result.append(escaped)
          }
        }
        else -> result.append(c)
      }
    }
  }

  fun skipValue() {
    when (val c = peek()) {
      '"' -> string()
      '{', '[' -> {
        val close = if (c == '{') '}' else ']'
        i++
        skipTrivia()
        while (peek() != close) {
          when (peek()) {
            '"' -> string()
            '{', '[' -> skipValue()
            '}', ']' -> fail()
            else -> i++
          }
          skipTrivia()
        }
        i++
      }
      else -> {
        val start = i
        while (i < s.length && s[i] !in ",:]}" && !s[i].isWhitespace() && !s.startsWith("/", i)) i++
        if (i == start) fail()
      }
    }
  }
}
