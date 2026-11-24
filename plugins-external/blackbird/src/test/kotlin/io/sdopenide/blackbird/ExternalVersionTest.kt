// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.blackbird

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalVersionTest {
  @Test
  fun v1PrintsBareVersion() = assertEquals("1.19.5.8 external", externalVersion("1.19.5.8\n"))

  @Test
  fun v2PrintsNameAndVersion() = assertEquals("2.2.5.8 external", externalVersion("blackbird v2.2.5.8\n"))

  @Test
  fun noVersion() = assertEquals("external", externalVersion("dev build"))

  @Test
  fun majorVersionStillPicksStandalone() =
    assertEquals("blackbird --standalone", sessionCommand(externalVersion("blackbird v2.2.5.8"), false, java.nio.file.Path.of("/c")))
}
