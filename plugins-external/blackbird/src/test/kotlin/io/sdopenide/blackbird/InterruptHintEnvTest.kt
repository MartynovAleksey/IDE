// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class InterruptHintEnvTest {
  private val dir = Files.createTempDirectory("bb")

  @Test
  fun v2PassesContent() {
    assertEquals(mapOf("BLACKBIRD_CLI_CONFIG_CONTENT" to "{\"interrupt_hint\":\"ctrl+esc\"}"), interruptHintEnv("2.2.5.6", "ctrl+esc", dir))
  }

  @Test
  fun v1WritesTuiConfig() {
    val env = interruptHintEnv("1.19.5.6", "ctrl \"x\"", dir)
    assertEquals("{\"interrupt_hint\":\"ctrl \\\"x\\\"\"}", Files.readString(Path.of(env.getValue("BLACKBIRD_TUI_CONFIG"))))
  }

  @Test
  fun emptyHintPassesNothing() {
    assertEquals(emptyMap<String, String>(), interruptHintEnv("1.19.5.6", "", dir))
  }
}
