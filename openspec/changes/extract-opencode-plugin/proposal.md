## Why

OpenCode вшит в дистрибутив SD-OpenIDE: бинарники opencode скачиваются при сборке IDE в terminal-плагин, а панель живёт в его коде. Из-за этого opencode нельзя обновить без пересборки IDE, нельзя поставить в обычную IntelliJ IDEA 2026.2, а IDE не собирает кроссплатформенный дистрибутив. Hermes уже вынесен в отдельный плагин (`plugins-external/hermes/`); OpenCode нужно сделать так же и заодно обновить до последних версий веток 1.x и 2.x (задача MartynovAleksey/IDE#1).

## What Changes

- Новый плагин **OpenCode for IntelliJ** — отдельный Gradle-проект `plugins-external/opencode/` на IntelliJ Platform Gradle Plugin, как Hermes. Совместим с IntelliJ IDEA и SD-OpenIDE 2026.2 (сборка 262), зависит от встроенного terminal-плагина.
- Плагин даёт то же, что сейчас SD-OpenIDE: панель **OpenCode** (вкладки-сессии, «+», стрелка выбора версии, консоль после выхода), `opencode` в `PATH` терминалов, Escape в процесс opencode (в IntelliJ IDEA — своим dispatcher'ом, как у Hermes).
- **Бинарники opencode внутри zip плагина**, отдельный zip на каждую ОС: `opencode-intellij-<версия>-{macos-arm64,macos-x64,linux-x64,linux-arm64,windows-x64,windows-arm64}.zip`.
- **Обновление opencode**: по умолчанию `v1.19.5` (было `v1.19.4`), в списке «+» — `v2.2.5` (было `2.1.4`). Оба берутся из GitHub-релизов `anomalyco/opencode` и проверяются по SHA-256.
- Плагин **не бандлится** в SD-OpenIDE: его ставят отдельно, с диска или из GitHub-релиза `opencode-intellij-<версия>`.
- **BREAKING**: из SD-OpenIDE убираются OpenCode и его сборка. Удаляются классы `plugins/terminal/.../opencode/`, регистрации в `terminal.xml`, иконки, скачивание архивов в `CommunityRepositoryModules.kt`, а `buildCrossPlatformDistribution` возвращается к исходному значению. Остаются хуки, с которыми плагин работает в SD-OpenIDE как родной: место `OpenCode` в раскладке и `"OpenCode"` в `JBTerminalWidget.isTerminalToolWindow`.

## Capabilities

### New Capabilities
- `opencode-plugin`: плагин OpenCode для IntelliJ — панель, выбор версии, PATH, Escape, бинарники opencode в zip под каждую ОС, сборка и установка.

### Modified Capabilities
- `embedded-opencode`: требования о встроенном opencode (бинарник в дистрибутиве, панель, вкладки, версии, PATH, Escape) удаляются — их забирает `opencode-plugin`. Остаётся место кнопки OpenCode в раскладке SD-OpenIDE, теперь при установленном плагине.

## Impact

- Новый каталог `plugins-external/opencode/` (Gradle wrapper, `build.gradle.kts`, исходники, скрипт упаковки zip по ОС).
- SD-OpenIDE: `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/`, `plugins/terminal/resources/META-INF/terminal.xml`, `plugins/terminal/resources/icons/opencode{,_dark}.svg`, `platform/build-scripts/.../CommunityRepositoryModules.kt`, `build/src/.../IdeaCommunityProperties.kt`.
- Без изменений: `defaultToolWindowlayoutProvider.kt` (`addOrUpdate("OpenCode")`), `JBTerminalWidget.java`, `.ai/render-guides.mjs` (это конфиг opencode для разработки репозитория, не продукт).
- Размер zip плагина: два бинарника opencode по ОС, примерно 100–250 МБ.
- Пользователи SD-OpenIDE после обновления останутся без OpenCode, пока не поставят плагин.
