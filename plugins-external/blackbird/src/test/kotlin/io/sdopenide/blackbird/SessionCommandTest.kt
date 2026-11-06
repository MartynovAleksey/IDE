// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class SessionCommandTest {
  private val dir = Path.of("/cfg")

  @Test
  fun plain() {
    assertEquals("blackbird", sessionCommand("1.19.5.2", false, null))
    assertEquals("blackbird", sessionCommand("2.2.5.2", false, null))
  }

  @Test
  fun ownDirectoryNeedsStandaloneOnlyInV2() {
    assertEquals("blackbird", sessionCommand("1.19.5.2", false, dir))
    assertEquals("blackbird --standalone", sessionCommand("2.2.5.2", false, dir))
  }

  @Test
  fun opencodeCompatible() {
    assertEquals("blackbird --opencode-compatible", sessionCommand("2.2.5.2", true, null))
    assertEquals("blackbird --opencode-compatible", sessionCommand("1.19.5.2", true, dir))
    assertEquals("blackbird --opencode-compatible --standalone", sessionCommand("2.2.5.2", true, dir))
  }
}
