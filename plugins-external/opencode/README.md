# OpenCode for IntelliJ

opencode in IntelliJ IDEA and SD-OpenIDE 2026.2 (262): an OpenCode tool window with session tabs and a version picker,
and `opencode` on PATH of local terminals. Each zip carries opencode 1.19.5 (default) and 2.2.5 for one OS.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/opencode-intellij-0.1.0.zip build/dist
```

The first command compiles against the installed SD-OpenIDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the opencode archives, checks their SHA-256 and writes
`build/dist/opencode-intellij-0.1.0-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the opencode versions, edit `DEFAULT_VERSION` and `SHA256` in `package_plugin.py`; the hashes are the `digest`
of the assets in the [opencode releases](https://github.com/anomalyco/opencode/releases).

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip for your OS, restart the IDE.
