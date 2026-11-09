# OpenCode for IntelliJ

opencode in IntelliJ IDEA and SD-OpenIDE 2026.2 (262): an OpenCode tool window with session tabs and a version picker, settings,
and `opencode` on PATH of local terminals. Each zip carries opencode 1.19.5 (default) and 2.2.5 for one OS.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/opencode-intellij-0.2.1.zip build/dist
```

The first command compiles against the installed SD-OpenIDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the opencode archives, checks their SHA-256 and writes
`build/dist/opencode-intellij-0.2.1-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the opencode versions, edit `DEFAULT_VERSION` and `SHA256` in `package_plugin.py`; the hashes are the `digest`
of the assets in the [opencode releases](https://github.com/anomalyco/opencode/releases).

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip for your OS, restart the IDE.

## Settings

Settings | Tools | OpenCode:

- **Default version** — the opencode that the OpenCode button, "+" and `opencode` in Terminal run.
- **Configuration** — per version: its config directory (`opencode.json`, `agent/`, `command/`, `plugin/`, …), passed as
  `OPENCODE_CONFIG_DIR` and applied on top of the global `~/.config/opencode` (empty: the global directory only); `model`
  and `small_model` of the `opencode.json[c]` in it; the whole file, read-only, with Open in Editor and Reveal in Finder.

opencode 2 runs sessions in a shared background service that ignores a tab's `OPENCODE_CONFIG_DIR`, so its tabs with their own
directory start `opencode --standalone`; after exiting opencode in such a tab, start it the same way.
