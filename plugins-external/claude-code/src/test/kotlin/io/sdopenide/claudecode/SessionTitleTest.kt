// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class SessionTitleTest {
  private fun title(vararg lines: String): String? {
    val file = Files.createTempFile("session", ".jsonl")
    try { Files.write(file, lines.toList()); return sessionTitle(file) } finally { Files.delete(file) }
  }

  @Test
  fun firstTypedPromptSkippingMetaAndCommandWrappers() {
    assertEquals("fix the build", title(
      """{"type":"user","isMeta":true,"message":{"role":"user","content":"meta"}}""",
      """{"type":"user","message":{"role":"user","content":"<command-name>/model</command-name>"}}""",
      """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","content":"x"}]}}""",
      """{"type":"user","message":{"role":"user","content":[{"type":"text","text":"fix the build\nplease"}]}}""",
      """{"type":"user","message":{"role":"user","content":"second"}}"""))
  }

  @Test
  fun customTitleWins() {
    assertEquals("Mine", title("""{"type":"user","message":{"role":"user","content":"first"}}""", """{"type":"custom-title","customTitle":"Mine"}"""))
  }

  @Test
  fun noPromptNoSession() {
    assertNull(title("""{"type":"summary","summary":"x"}"""))
  }
}
