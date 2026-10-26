// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.sdopenide.hermes

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.JBTerminalPanel
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.UIUtil
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Escape in a Hermes tab goes to Hermes (it cancels prompts there) instead of moving focus to the editor, following the
 * Terminal setting "Move focus to the editor with" like the Terminal tool window: the platform applies it only to tool window
 * ids it knows and otherwise always moves the focus on Escape.
 */
internal fun keepEscapeInTerminals(toolWindow: ToolWindow) {
  // touches only Swing and the tty, so no write-intent lock is needed
  IdeEventQueue.getInstance().addDispatcher(object : IdeEventQueue.NonLockedEventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
      if (e !is KeyEvent || e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_ESCAPE || e.modifiersEx != 0) return false
      // only the terminal itself: Escape in its search field still closes the search
      val source = e.component as? JBTerminalPanel ?: return false
      if (!SwingUtilities.isDescendingFrom(source, toolWindow.component)) return false
      // Settings | Tools | Terminal | "Move focus to the editor with" set to Escape: the platform moves the focus, as in Terminal
      if (escapeMovesFocusToEditor()) return false
      val tty = UIUtil.getParentOfType(JBTerminalWidget::class.java, source)?.ttyConnector ?: return false
      tty.write("\u001b")
      return true
    }
  }, toolWindow.disposable)
}

private fun escapeMovesFocusToEditor(): Boolean {
  val action = ActionManager.getInstance().getAction("Terminal.SwitchFocusToEditor") ?: return false
  return KeymapUtil.getKeyStrokes(action.shortcutSet).any { it.keyCode == KeyEvent.VK_ESCAPE && it.modifiers == 0 }
}
