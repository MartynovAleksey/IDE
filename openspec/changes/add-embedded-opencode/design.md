## Context

- Панель Terminal регистрируется в `plugins/terminal/resources/META-INF/terminal.xml` (`<toolWindow id="Terminal" anchor="bottom" …>`), модуль `intellij.terminal`.
- Новый терминал (reworked) создаёт свои виджеты через `internal`-код (`TerminalViewFactory`), поэтому снаружи его использовать нельзя. Публичный API без `@Deprecated` — `AbstractTerminalRunner.startShellTerminalWidget(parent, ShellStartupOptions, deferSessionStartUntilUiShown)`. Он возвращает `TerminalWidget` с `component`, который можно вложить в любую панель. Команда задаётся через `ShellStartupOptions.Builder().shellCommand(listOf(...))`.
- Порядок кнопок нового интерфейса по умолчанию задан в `defaultToolWindowlayoutProvider.kt`, `addPlatformDefaultsV2()`, нижняя полоса: `… "Terminal", "Services"`. Порядок определяется порядком вставки.
- Раскладка terminal-плагина в дистрибутиве: `CommunityRepositoryModules.kt`, `pluginAuto("intellij.terminal") { … withResource("resources/shell-integrations", …) }`. В `PluginLayout` есть `withGeneratedPlatformResources(os, arch, libc) { targetDir, context -> }` и `withPlatformExecutable(os, arch, libc, pattern)`, а в build-scripts — `downloadFileToCacheLocation(url, communityRoot)`.
- Архив `opencode-darwin-arm64.zip` v1.19.4: 64 МБ, внутри один файл `opencode` (Mach-O arm64, 181 МБ, подписан автором, TeamIdentifier `5NZ4Q7NXJ4`). SHA-256 `c400a2f8c8ab7e99478a7f4dd1a466b57bf33888e520d85c55780a5464c4b689`.

## Goals / Non-Goals

**Goals:**
- Панель OpenCode, которая открывается так же, как Terminal, и запускает встроенный opencode в корне проекта.
- Кнопка в раскладке по умолчанию стоит между Terminal и Services.
- Бинарник едет в `.dmg` для macOS arm64 и проверяется по SHA-256.

**Non-Goals:**
- opencode для Intel Mac, Linux и Windows.
- Настройки opencode, вход в аккаунт и провайдеры моделей: пользователь делает это внутри opencode.
- Автообновление opencode, выбор версии, несколько вкладок, кнопка перезапуска.
- Интеграция opencode с редактором (передача выделения, открытие файлов).

## Decisions

1. **Код внутри terminal-плагина, а не новый плагин.** Фабрика панели — один файл в `plugins/terminal/src/.../opencode/`, регистрация — строка в `terminal.xml`. Так она получает доступ к API терминала без нового модуля, `.iml`, `BUILD.bazel` и записи в списке плагинов. Альтернатива — отдельный плагин `intellij.sd.opencode` — потребовала бы генерации Bazel-таргетов из JPS-модели и регистрации в раскладке продукта.
2. **Классический виджет через `startShellTerminalWidget`.** Это единственный публичный неустаревший способ получить терминальный компонент для своей панели. opencode — TUI-приложение, и классический JediTerm его отображает. Альтернатива — вкладка нового терминала через `TerminalToolWindowTabsManager` — открывается только в панели Terminal, а не в отдельной.
3. **Ленивый запуск.** Процесс стартует в `createToolWindowContent`, то есть при первом открытии панели, и останавливается вместе с `toolWindow.disposable` при закрытии проекта. При пустом проекте рабочий каталог — домашний.
4. **Путь к бинарнику:** каталог terminal-плагина (`PluginManagerCore.getPlugin(<id terminal-плагина>).pluginPath`) + `bin/opencode`. Если файла нет, вместо терминала показывается `JBLabel` с сообщением и ожидаемым путём.
5. **Кнопка между Terminal и Services:** одна строка `addOrUpdate("OpenCode")` в `addPlatformDefaultsV2()`. Альтернатива — своё расширение `DefaultToolWindowLayoutExtension` в terminal-плагине — добавляло бы кнопку после Services, потому что расширения применяются после платформенного списка.
6. **Скачивание при сборке, а не бинарник в git.** Файл весит 181 МБ, а GitHub не принимает файлы больше 100 МБ. В раскладке terminal-плагина:
   - `withGeneratedPlatformResources(OsFamily.MACOS, JvmArchitecture.aarch64, MacLibcImpl.DEFAULT)` скачивает архив через `downloadFileToCacheLocation` (кэш в `~/.cache`, повторно не качает), сверяет SHA-256 и извлекает `opencode` в `bin/opencode`;
   - `withPlatformExecutable(…, "bin/opencode")` ставит право на исполнение в `.dmg`.
   URL и хэш — константы рядом с этим кодом.
7. **Иконка:** простая собственная SVG `opencode.svg` (16×16) в ресурсах terminal-плагина, чтобы кнопка отличалась от Terminal. Логотип opencode не используется.

## Risks / Trade-offs

- [Сохранённая раскладка у существующего пользователя] → новая панель добавится в конец полосы, а не между Terminal и Services. Порядок восстанавливает Window | Restore Default Layout. Каталог `SDOpenIDE2026.2` новый, поэтому у большинства раскладка будет по умолчанию.
- [Сборка без доступа к github.com] → шаг упадёт при первой сборке; после первой загрузки архив берётся из кэша.
- [Классический виджет терминала устарел] → `startShellTerminalWidget` сейчас без `@Deprecated`; при переходе на новый тег это нужно проверять.
- [Unsigned `.app` с подписанным бинарником внутри] → Gatekeeper будет требовать того же ручного разрешения, что и сейчас для всего приложения; подпись не входит в задачу.
- [Правки в коде JetBrains: terminal-плагин, платформа, build-scripts] → при обновлении тега возможны конфликты; правки маленькие и собраны в четырёх файлах.
- [Лицензия opencode] → проект распространяется под MIT; в архиве нет файла лицензии. Перед распространением `.dmg` третьим лицам стоит добавить текст лицензии opencode в `NOTICE` (Open Question).
- [Размер] → `.app` +181 МБ, `.dmg` +64 МБ.

## Migration Plan

Новая сборка `.dmg`. Откат — `git revert` коммита; бинарник при этом просто перестаёт попадать в сборку.

## Open Questions

- Нужен ли текст лицензии opencode в `NOTICE` дистрибутива сейчас?
- Нужна ли позже панель для Intel Mac (`opencode-darwin-x64.zip`)?
