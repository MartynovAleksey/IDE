# Claude Code CLI for IntelliJ

A Claude Code tool window (right side) in IntelliJ IDEA 2026.2 (262): each tab is a local terminal in the project root that runs
`claude`; "+" opens another session, Escape goes to claude. Each zip carries the Claude Code CLI 2.1.319 for one OS, put first on
PATH of local terminals with its self-update off (the plugin delivers new versions). Without a bundled binary the plugin falls
back to an installed `claude` on PATH or in `~/.local/bin`.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/claude-code-intellij-0.2.0.zip build/dist
```

The first command compiles against the installed IDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the claude binaries from `downloads.claude.ai`, checks their SHA-256 and writes
`build/dist/claude-code-intellij-0.2.0-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the claude version, edit `VERSION` and `SHA256` in `package_plugin.py`; the hashes are in
`https://downloads.claude.ai/claude-code-releases/<version>/manifest.json`.

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip for your OS, restart the IDE.

## Settings

Settings | Tools | Claude Code: `ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` (kept in the IDE password safe), passed to new
sessions only when set.
