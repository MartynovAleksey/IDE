# OpenCode for IntelliJ

opencode in IntelliJ IDEA and SD-OpenIDE 2026.2 (262): an OpenCode tool window with session tabs and a version picker, settings,
and `opencode` on PATH of local terminals. Each zip carries opencode 1.19.5 (default) and 2.2.5 for one OS.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/opencode-intellij-0.2.0.zip build/dist
```

The first command compiles against the installed SD-OpenIDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the opencode archives, checks their SHA-256 and writes
`build/dist/opencode-intellij-0.2.0-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the opencode versions, edit `DEFAULT_VERSION` and `SHA256` in `package_plugin.py`; the hashes are the `digest`
of the assets in the [opencode releases](https://github.com/anomalyco/opencode/releases).

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip for your OS, restart the IDE.

## Settings

Settings | Tools | OpenCode:

- **Default version** — the opencode that the OpenCode button, "+" and `opencode` in Terminal run.
- **Configuration** — per version: the path to its `opencode.json`, passed as `OPENCODE_CONFIG` and applied on top of the
  global `~/.config/opencode/opencode.json` (empty: the global file only); `model` and `small_model` of that file; the whole
  file, read-only, with Open in Editor.

opencode 2 runs sessions in a shared background service that ignores a tab's `OPENCODE_CONFIG`, so its tabs with their own
file start `opencode --standalone`; after exiting opencode in such a tab, start it the same way.
