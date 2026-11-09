// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package io.sdopenide.claudecode

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

private const val BASE_URL_KEY = "io.sdopenide.claudecode.baseUrl"
private val TOKEN = CredentialAttributes(generateServiceName("Claude Code", "ANTHROPIC_AUTH_TOKEN"))

/** ANTHROPIC_BASE_URL in the IDE options, ANTHROPIC_AUTH_TOKEN in the IDE password safe. */
internal object ClaudeCodeSettings {
  var baseUrl: String
    get() = PropertiesComponent.getInstance().getValue(BASE_URL_KEY, "")
    set(value) = PropertiesComponent.getInstance().setValue(BASE_URL_KEY, value.trim(), "")

  var authToken: String
    get() = PasswordSafe.instance.getPassword(TOKEN).orEmpty()
    set(value) = PasswordSafe.instance.setPassword(TOKEN, value.trim().ifEmpty { null })

  /** The env of a new claude session: only the values that are set, so empty ones leave claude's own login and endpoint. */
  fun env(): Map<String, String> = buildMap {
    baseUrl.takeIf { it.isNotEmpty() }?.let { put("ANTHROPIC_BASE_URL", it) }
    authToken.takeIf { it.isNotEmpty() }?.let { put("ANTHROPIC_AUTH_TOKEN", it) }
  }
}

/** Settings | Tools | Claude Code. */
internal class ClaudeCodeSettingsConfigurable : BoundConfigurable("Claude Code") {
  override fun createPanel(): DialogPanel = panel {
    row("ANTHROPIC_BASE_URL:") {
      textField().align(AlignX.FILL).bindText(ClaudeCodeSettings::baseUrl)
        .comment("Empty: the Anthropic API")
    }
    row("ANTHROPIC_AUTH_TOKEN:") {
      passwordField().align(AlignX.FILL).bindText(ClaudeCodeSettings::authToken)
        .comment("Stored in the IDE password safe. Empty: claude's own login")
    }
    row { comment("Applies to new Claude Code sessions") }
  }
}
