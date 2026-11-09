// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import org.junit.Assert.assertEquals
import org.junit.Test

class JsStringTest {
  @Test
  fun escapesQuotesBackslashesAndLineBreaks() {
    assertEquals("\"a\\\"b\\\\c\\u000ad\\u2028\"", jsString("a\"b\\c\nd "))
  }
}
