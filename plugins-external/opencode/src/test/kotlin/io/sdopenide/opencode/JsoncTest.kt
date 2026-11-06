// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsoncTest {
  private val config = """
    {
      // models
      "${'$'}schema": "https://opencode.ai/config.json",
      "model": "anthropic/a", /* inline */
      "provider": { "x": { "options": { "url": "http://h/{a}" } } },
      "list": [1, "two", { "n": null }]
    }
  """.trimIndent()

  @Test
  fun `reads top-level strings only`() {
    assertEquals("anthropic/a", Jsonc.getString(config, "model"))
    assertNull(Jsonc.getString(config, "url"))
    assertNull(Jsonc.getString(config, "small_model"))
  }

  @Test
  fun `replaces a value and keeps comments`() {
    assertEquals(config.replace("\"anthropic/a\"", "\"openai/\\\"b\\\"\""), Jsonc.setString(config, "model", "openai/\"b\""))
  }

  @Test
  fun `adds a member after the last one`() {
    assertEquals(config.replace("{ \"n\": null }]", "{ \"n\": null }],\n  \"small_model\": \"s\""), Jsonc.setString(config, "small_model", "s"))
    assertEquals("{\n  \"model\": \"m\"\n}", Jsonc.setString("{}", "model", "m"))
    assertEquals("{\n  \"a\": 1,\n  \"model\": \"m\",\n}", Jsonc.setString("{\n  \"a\": 1,\n}", "model", "m"))
  }

  @Test
  fun `removes a member with its line`() {
    assertEquals(config.replace("  \"model\": \"anthropic/a\", /* inline */\n", "/* inline */\n"), Jsonc.setString(config, "model", null))
    assertEquals("{\n  \"a\": 1\n}", Jsonc.setString("{\n  \"a\": 1,\n  \"model\": \"m\"\n}", "model", null))
    assertEquals("{ \"b\": 2 }", Jsonc.setString("{ \"model\": \"m\", \"b\": 2 }", "model", null))
    assertEquals("{\"a\": 1}", Jsonc.setString("{\"a\": 1}", "model", null))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a non-string model`() {
    Jsonc.getString("{\"model\": {\"id\": 1}}", "model")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects malformed text`() {
    Jsonc.setString("{\"model\": \"m\" \"b\": 1}", "model", "x")
  }
}
