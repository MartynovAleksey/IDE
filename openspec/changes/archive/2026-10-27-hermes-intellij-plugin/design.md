## Context

- Незакоммиченная реализация встроила Hermes в SD-OpenIDE: `build/sd-hermes/make_portable_hermes.sh` (переносимый Hermes, проверен на macOS arm64: `v0.22.5`, `Python 3.14.6`), скачивание архивов в `CommunityRepositoryModules.kt`, панель/настройки в `plugins/terminal/.../opencode/Hermes*`, общая логика вкладок `AgentSessionTabs.kt`.
- Hermes без своих инструментов пишет `install out of sync (ffmpeg, node, npm, ripgrep) — this unknown-managed install must rebuild the artifact`: `hermes_cli/venv_sync.check_runtime` вызывает `pm.activate()`, а установка с `install-stamp.json` считается «запечатанной» и сама инструменты не ставит.
- `pm/lock.json` Hermes содержит для `node` 26.12.1, `npm` 12.0.2, `ripgrep` 15.2.0, `ffmpeg` 9.0.1 и др. адреса и SHA-256 по платформам (`darwin-arm64`, `linux-x64`, `linux-arm64`, `win32-x64`, …); `pm.paths.store_root()` задаёт хранилище.
- Нужные API terminal-плагина (`LocalTerminalDirectRunner.startShellTerminalWidget`, `shellStartupOptions`, `ShellExecOptionsCustomizer`) публичны и есть в IntelliJ IDEA 2026.2. Обработка Escape (`JBTerminalWidget.isTerminalToolWindow`) — платформенная; в SD-OpenIDE id `Hermes` уже учтён, в IntelliJ IDEA — нет.
- На машине есть JDK 21, Gradle — через wrapper.

## Goals / Non-Goals

**Goals:**
- Отдельный плагин, ставится в IntelliJ IDEA и SD-OpenIDE 262.
- Полноценный Hermes с инструментами `ripgrep`, `node`, `npm`, `ffmpeg` в zip под каждую из 4 ОС.
- Панель, настройки, PATH, Escape — как в текущей реализации.
- SD-OpenIDE без Hermes.

**Non-Goals:**
- macOS x64 и Windows arm64 (нет wheel `cryptography 50.0.1`).
- Публикация в JetBrains Marketplace (размер zip); раздача — GitHub-релизы и установка с диска.
- Тяжёлые необязательные пакеты `pm` (`chromium`, `llamacpp-*`, `cua-driver` и т.п.) — только то, что Hermes требует для «полной» установки.
- Подпись плагина.

## Decisions

1. **Gradle-проект `plugins-external/hermes/`** на IntelliJ Platform Gradle Plugin 2.19: плагин делается для SD-OpenIDE и компилируется против установленной SD-OpenIDE (`local(ideaPath)`, по умолчанию `/Applications/SD-OpenIDE.app`, `-PideaPath=…`), без скачивания IntelliJ IDEA; `bundledPlugin("org.jetbrains.plugins.terminal")`, `sinceBuild = 262`, `untilBuild = 262.*`. Отдельный от Bazel-сборки монорепозитория — его можно вынести в свой репозиторий без изменений.
2. **Код панели и настроек переезжает в плагин** (пакет `io.sdopenide.hermes`): `HermesToolWindowFactory`, `HermesSettingsConfigurable`, `HermesCli`, PATH-customizer, иконка. Общая логика вкладок копируется в плагин (~70 строк): из стороннего плагина нельзя вызвать код SD-OpenIDE.
3. **Payload — каталог `hermes/` внутри плагина** (`<plugin>/hermes/{bin,python,site-packages,app,tools…}`), путь — через `PluginManagerCore.getPlugin(<id>).pluginPath`. Скрипт `make_portable_hermes.sh` переезжает в `plugins-external/hermes/payload/` и дополняется инструментами.
4. **Инструменты ставит штатный `pm` Hermes** (проверено на macOS arm64): `python -m pm.cli install --tools-only --without agent-browser --without cua-driver` с `HERMES_RUNTIME_DIR=<payload>/tools` и временным `HERMES_HOME` кладёт `ffmpeg`, `node`, `npm`, `ripgrep` (+ свой `python`, `uv`) в `<payload>/tools`; затем рядом с `app/` пишется `manifest.json` `{"repo": "app", "store": "tools", "runtime": {"commands": {"hermes": "bin/hermes"}}}` — как у Hermes Desktop. С ним `pm.paths.store_root()` указывает в payload, `venv_sync.check_runtime()` возвращает `None` (нет `install out of sync`), payload переносим (в `facts.json` нет абсолютных путей). macOS arm64: 732 МБ распакованно. Рядом с `manifest.json` лежит `pm-runtime/` (Hermes `stage_manager_runtime` + `seal_pm_runtime`, как в его собственных payload): без него `pm.ensure("node")` падает с `packaged PM runtime is missing`. TUI (`display.interface: tui`) собирается заранее скриптами Hermes `node-deps.mjs`/`tui.mjs` node из payload в `hermes_cli/tui_dist`, чтобы `hermes --tui` работал без сети. Зависимости лежат как venv (`venv/lib/python3.14/site-packages` + `hermes-agent.pth`), объявленный в `manifest.json` (`"venv"`, `runtime.storePython`): процессы, которые Hermes запускает только с `app/` в `PYTHONPATH` (шлюз TUI), находят зависимости сами.
4a. **Сборка payload — нативно в GitHub Actions**, а не кросс-сборкой на Mac: `pm` проверяет скачанные бинарники запуском (`uv --version` → `Exec format error` для Linux на Mac), а Linux-сборки CPython содержат имена, различающиеся только регистром (ломаются на регистронезависимом APFS). Workflow `.github/workflows/hermes-payload.yml` (ручной запуск, параметр — версия Hermes) на `macos-14` (arm64), `ubuntu-24.04` (x64), `ubuntu-24.04-arm` (arm64), `windows-2025` (x64) запускает `plugins-external/hermes/payload/build_payload.py`, который там же проверяет `hermes --version` и `check_runtime() is None`, и выкладывает `hermes-payload-<версия>-<ОС>.tar.gz` в релиз `hermes-payload-<версия>`. Скрипт на Python (не bash), чтобы одинаково работать на Windows (нет `rsync`).
5. **Zip на ОС**: `./gradlew buildPlugin` даёт zip без payload; `payload/package_plugin.py <zip> <hermes-payload-…-<ОС>.tar.gz> <dir>` переносит записи tar прямо в zip с Unix-правами и симлинками (`npm`/`npx` — симлинки) → `hermes-intellij-<версия>-<ОС>.zip`. Gradle Zip разыменовывает симлинки, а распаковка Linux-payload на macOS ломается на регистре имён.
6. **Права на исполнение**: отдельного восстановления нет — установщик плагинов IDE распаковывает zip через `Decompressor.Zip(...).withZipExtensions()`, который восстанавливает Unix-права и симлинки.
7. **Escape в IntelliJ IDEA**: плагин регистрирует `IdeEventQueue` dispatcher: если фокус внутри панели `Hermes` и нажат Escape без модификаторов — пишет `ESC` в tty вкладки и поглощает событие до `TerminalEscapeKeyListener`. В SD-OpenIDE это дублирует платформенную обработку безвредно.
8. **Удаление из SD-OpenIDE**: убираются `HERMES_*`, `BUNDLE_HERMES`, генераторы Hermes в `CommunityRepositoryModules.kt`, `Hermes*`-классы, иконки и регистрации в `terminal.xml`, `build/sd-hermes/`. Остаются: `AgentSessionTabs.kt` (нужен OpenCode), `addOrUpdate("Hermes")` в раскладке и `"Hermes"` в `JBTerminalWidget.isTerminalToolWindow` — чтобы панель плагина в SD-OpenIDE вставала между OpenCode и Services и Escape работал без dispatcher'а.

## Risks / Trade-offs

- [Раскладка хранилища `pm` внутренняя для Hermes и может меняться между версиями] → привязана к Hermes 0.22.5; критерий проверки — `pm.activate()` пуст; при обновлении Hermes перепроверяется.
- [Сборка инструментов под чужую ОС на Mac] → инструменты берутся готовыми бинарниками из `pm/lock.json`, не собираются; запуск для Linux/Windows не проверить на Mac.
- [Размер zip 250–450 МБ] → не для Marketplace; GitHub-релиз (лимит 2 ГБ на файл).
- [Escape-dispatcher зависит от фокуса Swing] → проверяется вручную в IntelliJ IDEA.
- [Hermes и SD-OpenIDE оба пишут в `~/.hermes`] → ожидаемо, общий профиль.

## Migration Plan

Незакоммиченный встроенный Hermes заменяется плагином до коммита. SD-OpenIDE пересобирается без Hermes; плагин собирается отдельно и публикуется в GitHub-релиз `hermes-intellij-0.1.0` по подтверждению.

## Open Questions

- Нужны ли сверх `ripgrep`/`node`/`npm`/`ffmpeg` другие пакеты `pm` (например `gh`, `agent-browser`) — определяется тем, что `pm.activate()` считает обязательным.
