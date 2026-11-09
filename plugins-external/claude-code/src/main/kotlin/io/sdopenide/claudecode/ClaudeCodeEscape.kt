// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.claudecode

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.JBTerminalPanel
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.UIUtil
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Escape in a Claude Code tab goes to claude (it interrupts and closes its dialogs) instead of moving focus to the editor, as in the Terminal
 * tool window: the platform keeps Escape only for the tool window ids it knows.
 */
internal fun keepEscapeInTerminals(toolWindow: ToolWindow) {
  // touches only Swing and the tty, so no write-intent lock is needed
  IdeEventQueue.getInstance().addDispatcher(object : IdeEventQueue.NonLockedEventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
      if (e !is KeyEvent || e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_ESCAPE || e.modifiersEx != 0) return false
      // only the terminal itself: Escape in its search field still closes the search
      val source = e.component as? JBTerminalPanel ?: return false
      if (!SwingUtilities.isDescendingFrom(source, toolWindow.component)) return false
      val tty = UIUtil.getParentOfType(JBTerminalWidget::class.java, source)?.ttyConnector ?: return false
      tty.write("\u001b")
      return true
    }
  }, toolWindow.disposable)
}

/**
 * Escape in a chat goes to the page ([onEscape]: closes its menus or stops claude) instead of the tool window, which would move
 * focus to the editor so that the next keys typed for the chat land in code.
 */
internal fun keepEscapeInChat(chat: Component, parent: Disposable, onEscape: () -> Unit) {
  IdeEventQueue.getInstance().addDispatcher(object : IdeEventQueue.NonLockedEventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
      if (e !is KeyEvent || e.keyCode != KeyEvent.VK_ESCAPE || e.modifiersEx != 0) return false
      // JCEF may post the key from another component than the one with focus: either being in the chat counts
      val inChat = listOf(e.component, KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
        .any { it != null && (it === chat || SwingUtilities.isDescendingFrom(it, chat)) }
      if (!inChat) return false
      if (e.id == KeyEvent.KEY_PRESSED) onEscape()
      e.consume()
      return true
    }
  }, parent)
}
