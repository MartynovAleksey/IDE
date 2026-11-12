# Blackbird for IntelliJ

[blackbird](https://github.com/MartynovAleksey/blackbird) in IntelliJ IDEA and SD-OpenIDE 2026.2 (262): a Blackbird tool window
with session tabs and a version picker, settings, and `blackbird` on PATH of local terminals. Each zip carries blackbird
1.19.5.6 (default, the `blackbird/v1` line) and 2.2.5.6 (`blackbird/v2`) for one OS.

## Build

```bash
./gradlew buildPlugin
python3 package_plugin.py build/distributions/blackbird-intellij-0.1.4.zip build/dist
```

The first command compiles against the installed SD-OpenIDE (`ideaPath` in `gradle.properties`, or `-PideaPath=...`).
The second downloads the blackbird archives and writes
`build/dist/blackbird-intellij-0.1.4-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`;
pass OS names after the output directory to build only those.

To change the blackbird versions, edit `DEFAULT_VERSION` and `VERSIONS` in `package_plugin.py`.

`BLACKBIRD_ARCHIVES` tells `package_plugin.py` where the release archives (`blackbird-v<version>-<os>.tar.gz|.zip`) are: a
directory or a URL, where `{version}` stands for the version. Without it they are downloaded from the blackbird GitHub
releases into `build/blackbird-archives/`, which also fits as a directory:
`BLACKBIRD_ARCHIVES='build/blackbird-archives/{version}'`. Archives are not checked against a checksum, so any build of those versions fits.

### In Docker

The image has everything but the sources and blackbird: JDK 21, Python, SD-OpenIDE for Linux to compile against and the Gradle
dependencies, so the build runs offline except for the blackbird archives.

```bash
docker build -f docker/Dockerfile -t blackbird-intellij-build .
docker run --rm -v "$PWD:/src" blackbird-intellij-build
```

The container runs the tests and writes the six zips and `SHA256SUMS.txt` to `build/dist` of the mounted sources; pass OS names
after the image name to build only those. Archives from another place:

```bash
docker run --rm -v "$PWD:/src" -e BLACKBIRD_ARCHIVES='https://example.com/blackbird/v{version}' blackbird-intellij-build
docker run --rm -v "$PWD:/src" -v /path/to/archives:/archives -e BLACKBIRD_ARCHIVES='/archives/{version}' blackbird-intellij-build macos-arm64
```

Rebuild the image after changing the Gradle dependencies or the IDE version (`--build-arg SD_OPENIDE_VERSION=...`).

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
  - **Interrupt key hint**, `ctrl+esc` by default: the key text blackbird shows in its interrupt hint (`interrupt_hint`).
    Escape does not reach blackbird in IDE terminals while Ctrl+Esc does, so tabs and Terminal show "ctrl+esc interrupt".
    Only the text changes, not the keys. The value goes in `BLACKBIRD_CLI_CONFIG_CONTENT` (2.x) or in a tui.json passed as
    `BLACKBIRD_TUI_CONFIG` (1.x), so the user's own files stay as they are; leave it empty for blackbird's own "esc";
  - `model` and `small_model` of the config file of the directory in use (`blackbird.json[c]`, or `opencode.json[c]` in the
    opencode-compatible mode); the whole file, read-only, with Open in Editor and Reveal in Finder.
- **Import from opencode** — runs `blackbird --opencode-import [path]` of the version chosen above in the project directory
  and shows its report. Without a path it copies opencode's global config, data and state to blackbird's global directories;
  a file or directory is imported next to itself with `opencode` renamed to `blackbird`. Overwritten files are kept as `.bak`.
  The session database is imported only by a blackbird of the same or a newer major version than the opencode that wrote it.

blackbird 2 runs sessions in a shared background service that ignores a tab's `BLACKBIRD_CONFIG_DIR`, so its tabs with their own
directory start with `--standalone`; after exiting blackbird in such a tab, start it the same way.
