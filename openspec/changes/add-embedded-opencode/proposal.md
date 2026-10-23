## Why

В SD-OpenIDE нужен AI-агент opencode прямо в IDE, без отдельного терминала и отдельной установки. Пользователь должен открывать его кнопкой рядом с Terminal, а сам бинарник opencode должен приезжать в `.dmg` вместе с IDE.

## What Changes

- Новая панель (tool window) **OpenCode** в нижней полосе. В раскладке по умолчанию её кнопка стоит между **Terminal** и **Services**.
- Панель работает как Terminal: внутри тот же терминальный виджет, но вместо shell запускается `opencode` в каталоге проекта.
- Сборка для macOS arm64 скачивает `opencode-darwin-arm64.zip` v1.19.4 с GitHub, сверяет SHA-256 и кладёт бинарник в `.app` внутрь terminal-плагина (`plugins/terminal/bin/opencode`) с флагом исполняемого файла.
- Размер приложения вырастает примерно на 181 МБ, `.dmg` — примерно на 64 МБ.

## Capabilities

### New Capabilities
- `embedded-opencode`: панель OpenCode в IDE, запуск встроенного бинарника opencode и его упаковка в дистрибутив macOS arm64.

### Modified Capabilities
<!-- нет: product-branding не меняется -->

## Impact

- `plugins/terminal/resources/META-INF/terminal.xml`: регистрация панели.
- `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/`: фабрика панели (новый файл) и иконка в ресурсах.
- `platform/platform-impl/src/com/intellij/toolWindow/defaultToolWindowlayoutProvider.kt`: место кнопки в раскладке по умолчанию.
- `platform/build-scripts/src/org/jetbrains/intellij/build/CommunityRepositoryModules.kt`: скачивание и упаковка бинарника в раскладке terminal-плагина.
- Сборке нужен доступ к `github.com` (один раз: дальше архив лежит в кэше загрузок).
- Затрагивает код JetBrains (terminal-плагин и платформу), поэтому при переходе на новый тег эти правки придётся переносить.
