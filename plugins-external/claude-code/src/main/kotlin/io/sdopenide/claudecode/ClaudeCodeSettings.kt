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
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

private const val BASE_URL_KEY = "io.sdopenide.claudecode.baseUrl"
private val TOKEN = CredentialAttributes(generateServiceName("Claude Code", "ANTHROPIC_AUTH_TOKEN"))
private val HEADERS = CredentialAttributes(generateServiceName("Claude Code", "ANTHROPIC_CUSTOM_HEADERS"))

/** ANTHROPIC_BASE_URL in the IDE options, ANTHROPIC_AUTH_TOKEN and ANTHROPIC_CUSTOM_HEADERS in the IDE password safe. */
internal object ClaudeCodeSettings {
  var baseUrl: String
    get() = PropertiesComponent.getInstance().getValue(BASE_URL_KEY, "")
    set(value) = PropertiesComponent.getInstance().setValue(BASE_URL_KEY, value.trim(), "")

  var authToken: String
    get() = PasswordSafe.instance.getPassword(TOKEN).orEmpty()
    set(value) = PasswordSafe.instance.setPassword(TOKEN, value.trim().ifEmpty { null })

  /** `Name: Value` lines; in the password safe too, since headers often carry keys. */
  var customHeaders: String
    get() = PasswordSafe.instance.getPassword(HEADERS).orEmpty()
    set(value) = PasswordSafe.instance.setPassword(HEADERS, value.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null })

  /** The env of a new claude session: only the values that are set, so empty ones leave claude's own login and endpoint. */
  fun env(): Map<String, String> = buildMap {
    baseUrl.takeIf { it.isNotEmpty() }?.let { put("ANTHROPIC_BASE_URL", it) }
    authToken.takeIf { it.isNotEmpty() }?.let { put("ANTHROPIC_AUTH_TOKEN", it) }
    customHeaders.takeIf { it.isNotEmpty() }?.let { put("ANTHROPIC_CUSTOM_HEADERS", it) }
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
    row("ANTHROPIC_CUSTOM_HEADERS:") {
      textArea().rows(4).align(AlignX.FILL).bindText(ClaudeCodeSettings::customHeaders)
        .comment("One <code>Name: Value</code> per line, added to every API request. Stored in the IDE password safe")
    }.layout(RowLayout.LABEL_ALIGNED)
    row { comment("Applies to new Claude Code sessions") }
  }
}
