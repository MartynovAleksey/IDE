## Why

Встроенный opencode сейчас есть только в сборке для macOS arm64, а сама панель неудобна в работе:
- новую сессию не открыть;
- Escape не доходит до opencode, а переводит фокус в редактор;
- после выхода из opencode (Ctrl-C) панель «мертвая»: продолжить сессию (`opencode -s <id>`) можно только после перезапуска IDE.

## What Changes

- Сборка всех дистрибутивов SD-OpenIDE с opencode v1.19.4: macOS arm64 и Intel, Linux x64 и arm64, Windows x64 и arm64. Для каждого берётся свой архив с релиза и проверяется по SHA-256 из релиза.
- Кросс-платформенный архив (`.zip` без JBR для всех ОС) больше не собирается: в нём не может быть одного правильного бинарника opencode.
- Панель OpenCode получает вкладки сессий, как Terminal: кнопка «+» открывает новую сессию, вкладки закрываются, процесс вкладки завершается при её закрытии.
- Каждая вкладка — обычная консоль (shell), в которой сразу запускается `opencode`. После выхода из opencode (Ctrl-C) остаётся приглашение shell, где можно выполнить `opencode -s <id>` или любую другую команду.
- Каталог встроенного opencode добавляется в `PATH` всех локальных терминальных сессий IDE, поэтому команда `opencode` работает и в OpenCode, и в Terminal.
- Escape в панели OpenCode передаётся в opencode, а не переводит фокус в редактор (как в панели Terminal).

## Capabilities

### New Capabilities
<!-- нет -->

### Modified Capabilities
- `embedded-opencode`: панель становится набором вкладок-консолей с автозапуском opencode, Escape передаётся в процесс, opencode попадает в `PATH`, бинарник упаковывается во все шесть дистрибутивов.

## Impact

- `platform/build-scripts/src/org/jetbrains/intellij/build/CommunityRepositoryModules.kt`: таблица архивов и хэшей на шесть платформ вместо одного.
- `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`: `buildCrossPlatformDistribution = false`.
- `build/src/OpenSourceCommunityInstallersBuildTarget.kt`: Windows `.zip` больше не пропускается.
- `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/`: фабрика панели с вкладками и действием «+», customizer `PATH`.
- `plugins/terminal/resources/META-INF/terminal.xml`: `canCloseContents`, регистрация customizer.
- `platform/execution-impl/src/com/intellij/terminal/JBTerminalWidget.java`: панель OpenCode считается терминальной при обработке Escape.
- Время и место сборки: шесть дистрибутивов вместо одного; бинарник занимает 181–214 МБ в каждом.
