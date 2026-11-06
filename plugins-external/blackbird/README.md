# Blackbird for IntelliJ

[blackbird](https://github.com/MartynovAleksey/blackbird) in IntelliJ IDEA and SD-OpenIDE 2026.2 (262): a Blackbird tool window
with session tabs and a version picker, settings, and `blackbird` on PATH of local terminals. Each zip carries blackbird
1.19.5.2 (default, the `blackbird/v1` line) and 2.2.5.2 (`blackbird/v2`) for one OS.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/blackbird-intellij-0.1.0.zip build/dist
```

The first command compiles against the installed SD-OpenIDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the blackbird archives, checks their SHA-256 and writes
`build/dist/blackbird-intellij-0.1.0-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the blackbird versions, edit `DEFAULT_VERSION` and `SHA256` in `package_plugin.py`; the hashes are in `SHA256SUMS`
of the [blackbird releases](https://github.com/MartynovAleksey/blackbird/releases).

## Install

Settings | Plugins | ⚙ | Install Plugin from Disk…, pick the zip for your OS, restart the IDE.

## Settings

Settings | Tools | Blackbird, also in the "⋮" menu of the tool window:

- **Default version** — the blackbird that the Blackbird button, "+" and `blackbird` in Terminal run.
- **Configuration** — per version:
  - its config directory (`blackbird.json`, `agent/`, `command/`, `plugin/`, …), passed as `BLACKBIRD_CONFIG_DIR` and applied
    on top of the global `~/.config/blackbird` (empty: the global directory only);
  - **opencode-compatible mode**, on by default: tabs start `blackbird --opencode-compatible` and work on opencode's config, sessions, keys
    and background service; the optional **opencode config directory** is passed as `BLACKBIRD_CONFIG_DIR`, which blackbird
    takes the same way as `--opencode-compatible=<dir>`; Terminal gets `BLACKBIRD_OPENCODE_COMPATIBLE=1` instead of the flag;
  - `model` and `small_model` of the config file of the directory in use (`blackbird.json[c]`, or `opencode.json[c]` in the
    opencode-compatible mode); the whole file, read-only, with Open in Editor and Reveal in Finder.
- **Import from opencode** — runs `blackbird --opencode-import [path]` of the version chosen above in the project directory
  and shows its report. Without a path it copies opencode's global config, data and state to blackbird's global directories;
  a file or directory is imported next to itself with `opencode` renamed to `blackbird`. Overwritten files are kept as `.bak`.
  The session database is imported only by a blackbird of the same or a newer major version than the opencode that wrote it.

blackbird 2 runs sessions in a shared background service that ignores a tab's `BLACKBIRD_CONFIG_DIR`, so its tabs with their own
directory start with `--standalone`; after exiting blackbird in such a tab, start it the same way.
