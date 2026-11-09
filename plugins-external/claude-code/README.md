# Claude Code CLI for IntelliJ

A Claude Code tool window (right side) in IntelliJ IDEA 2026.2 (262): each tab is a local terminal in the project root that runs
`claude`; "+" opens another session, Escape goes to claude. The CLI is not bundled: install it first
(`curl -fsSL https://claude.ai/install.sh | bash`), the plugin finds it on PATH or in `~/.local/bin`.

## Settings

Settings | Tools | Claude Code: `ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` (kept in the IDE password safe), passed to new
sessions only when set.

## Build

```bash
./gradlew buildPlugin
```

Compiles against the installed IDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`) and writes
`build/distributions/claude-code-intellij-0.1.0.zip`.

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip, restart the IDE.
