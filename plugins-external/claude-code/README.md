# Claude Code CLI for IntelliJ

A Claude Code tool window (right side) in IntelliJ IDEA 2026.2 (262). "+" opens a chat: a page over `claude -p` in stream-json mode
with streamed Markdown answers (code with Copy, tables), tool cards (file paths open in the editor, edits as diffs with
Open diff in the IDE, subagents nested in their Task), permission prompts (Allow / Allow for session / Deny), questions with
options, plan approval, a pinned task list, model and permission mode pickers, context usage, `/` command and `@` file
completion, pasted or dropped images, and Stop (or Escape). The history button reopens an earlier chat of the project and
continues it (`--resume`). The console button opens a terminal tab that runs the `claude` TUI. Each zip carries the Claude Code CLI 2.1.319 for one OS, put first on
PATH of local terminals with its self-update off (the plugin delivers new versions). Without a bundled binary the plugin falls
back to an installed `claude` on PATH or in `~/.local/bin`.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/claude-code-intellij-0.4.5.zip build/dist
```

The first command compiles against the installed IDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the claude binaries from `downloads.claude.ai`, checks their SHA-256 and writes
`build/dist/claude-code-intellij-0.4.5-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the claude version, edit `VERSION` and `SHA256` in `package_plugin.py`; the hashes are in
`https://downloads.claude.ai/claude-code-releases/<version>/manifest.json`.

## Tests

```bash
./gradlew test
node --test src/test/js/markdown.test.mjs
```

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip for your OS, restart the IDE.

## Settings

Settings | Tools | Claude Code: `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN` and `ANTHROPIC_CUSTOM_HEADERS` (`Name: Value` per line; the last two kept in the IDE password safe), passed to new
sessions only when set.
