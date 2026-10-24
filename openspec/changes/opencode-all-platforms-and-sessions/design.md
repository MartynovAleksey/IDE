## Context

- Сейчас (`embedded-opencode`): `withGeneratedPlatformResources(MACOS, aarch64)` в раскладке terminal-плагина скачивает один архив и проверяет его хэш. `OpenCodeToolWindowFactory` создаёт одну вкладку и запускает бинарник напрямую как `shellCommand`, поэтому после выхода процесса вкладка мертва.
- Сборка знает шесть дистрибутивов (`SUPPORTED_DISTRIBUTIONS`): Windows x64/arm64, macOS x64/arm64, Linux x64/arm64 (glibc). `./installers.cmd` без `-Dintellij.build.target.*` собирает все.
- `OpenSourceCommunityInstallersBuildTarget` (цель `installers.cmd`) всегда пропускает Windows `.zip` (`WINDOWS_ZIP_STEP`), чтобы сэкономить диск GitHub runner, а на macOS ещё и `.exe` (`WINDOWS_EXE_INSTALLER_STEP`, «doesn't work well on build agents»). Выяснилось на первой сборке: раскладки Windows собирались, архивов не было.
- При сборке всех ОС build-scripts дополнительно собирают кросс-платформенный архив из Linux x64-раскладки (`buildCrossPlatformDistribution = true` в `IdeaCommunityProperties`).
- Релиз v1.19.4 отдаёт SHA-256 каждого ассета через GitHub API. В архивах по одному файлу в корне: `opencode` (Linux, macOS) и `opencode.exe` (Windows). Есть варианты `-baseline` (без AVX2) и `-musl`. IDE на musl не собирается.
- Escape: `TerminalEscapeKeyListener` переводит фокус в редактор по Escape в любом классическом терминале, кроме панели, для которой `JBTerminalWidget.isTerminalToolWindow()` вернул `true` (сейчас только id `Terminal`); там срабатывает только шорткат `Terminal.SwitchFocusToEditor`. Других вызовов `JBTerminalWidget.isTerminalToolWindow` нет.
- `TerminalWidget.sendCommandToExecute(String)` выполняет команду в запущенном shell. Точка расширения `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer` даёт `MutableShellExecOptions.prependEntryToPATH(Path)`. С интеграцией shell это работает и после rc-файлов, через `_INTELLIJ_FORCE_PREPEND_PATH`.

## Goals / Non-Goals

**Goals:**
- opencode во всех шести дистрибутивах, каждый архив проверяется по хэшу.
- Вкладки сессий с «+» и закрытием.
- Вкладка — консоль с автозапуском `opencode`, которая переживает выход opencode.
- `opencode` в `PATH` терминалов IDE.
- Escape доходит до opencode.

**Non-Goals:**
- Linux на musl и варианты `-baseline` для старых x64-процессоров без AVX2.
- Подпись и нотаризация `.dmg` и `.exe`.
- Восстановление вкладок OpenCode после перезапуска IDE (Terminal это умеет, здесь не нужно).
- Кнопка «продолжить последнюю сессию» и прочая интеграция с opencode; продолжение — вручную через `opencode -s <id>`.

## Decisions

1. **Таблица платформ в одном месте.** В `CommunityRepositoryModules.kt` — список из шести записей (ОС, архитектура, libc, имя архива, SHA-256). Для каждой — `withGeneratedPlatformResources(...)`, а для Unix — `withPlatformExecutable(..., "bin/opencode")`. Распаковка через `BuildDependenciesDownloader.extractFile`, который понимает и `.zip`, и `.tar.gz`. Хэши взяты из GitHub API релиза; `darwin-arm64`, `linux-x64` и `windows-x64` дополнительно сверены по скачанным файлам.
2. **Обычные варианты, без `-baseline`.** Это основные сборки релиза. Процессоры без AVX2 (до ~2013 года) не поддерживаются — записано в Non-Goals.
3. **Без кросс-платформенного архива:** `buildCrossPlatformDistribution = false`. В такой архив попал бы бинарник только одной ОС (из Linux x64-раскладки), и на других ОС панель не работала бы. Пользователю нужны четыре варианта (две архитектуры Mac, Linux, Windows), а не этот архив.
4. **Вкладка = shell + `sendCommandToExecute("opencode")`.** Вместо прямого запуска бинарника вкладка стартует обычный локальный shell пользователя (как Terminal), а потом выполняет в нём `opencode`. После Ctrl-C остаётся приглашение shell. Альтернатива `shellCommand = [shell, -c, "opencode; exec $SHELL"]` зависит от конкретного shell и плохо работает в PowerShell.
5. **`PATH` через `ShellExecOptionsCustomizer`.** Свой customizer в terminal-плагине добавляет `<terminal plugin>/bin` в начало `PATH`, если там есть бинарник. Действует на все локальные терминалы IDE, поэтому `opencode` работает и в Terminal (раньше там было `command not found`). Альтернатива — переменная окружения только для вкладок OpenCode — сломалась бы, как только rc-файлы shell перезапишут `PATH`; механизм `_INTELLIJ_FORCE_PREPEND_*` это учитывает.
6. **Вкладки.** У каждой вкладки свой `Disposable`, связанный с `Content` (`content.setDisposer`). Виджет создаётся на этом `Disposable`, поэтому закрытие вкладки завершает процесс. Действие «New Session» («+») стоит сразу после вкладок (`ToolWindowEx.setTabActions`, как в Terminal; `setTitleActions` уводил его в правую часть заголовка, где его не было видно). `canCloseContents="true"`. Если вкладок не осталось, при следующем показе панели (`ToolWindowManagerListener.toolWindowShown`) создаётся новая. Имена вкладок: `OpenCode`, `OpenCode (2)`, …
8. **Windows `.zip` включён, `.exe` на macOS — нет.** `WINDOWS_ZIP_STEP` убран из списка пропусков `OpenSourceCommunityInstallersBuildTarget`, поэтому `.zip` (x64, arm64) — артефакт Windows при сборке на Mac. Пропуск `.exe` на macOS оставлен, как решили JetBrains; на Linux (и в CI) `.exe` собирается.
7. **Escape:** `JBTerminalWidget.isTerminalToolWindow()` возвращает `true` и для id `OpenCode`. Это одна строка; метод вызывается только из `TerminalEscapeKeyListener`, так что ничего другого не меняется.

## Risks / Trade-offs

- [`sendCommandToExecute` до готовности shell] → классический виджет ставит команду в очередь до старта сессии; проверить на zsh (macOS) при реализации.
- [Сборка всех ОС на Mac идёт дольше и занимает больше места: шесть раскладок, каждая со своим JBR и бинарником 181–214 МБ] → это осознанная цена; одну платформу по-прежнему можно собрать через `-Dintellij.build.target.os/arch`.
- [Linux и Windows проверяются только по содержимому архивов] → запустить их на Mac нельзя; проверка — наличие `bin/opencode[.exe]`, права на исполнение и тип файла (`file`).
- [`opencode` в `PATH` всех терминалов IDE] → может перекрыть opencode, который пользователь установил сам. Это ожидаемо для встроенной версии; свой можно вызвать по полному пути.
- [Правки в коде JetBrains: build-scripts, execution-impl, terminal] → маленькие, помечены комментарием `SD-OpenIDE`.

## Migration Plan

Новая сборка всех дистрибутивов. Откат — `git revert`; для одной платформы можно ограничить сборку флагами.

## Open Questions

- Нужна ли поддержка Linux на musl или старых x64-процессоров (`-baseline`)?

## Дополнение: кнопка «+» и выбор версии

9. **«+» через `ToolWindowEx.setTabActions`** — так же, как Terminal (`TerminalToolwindowActionGroup`). `setTitleActions` уводил кнопку в правую часть заголовка.
10. **Дополнительные версии в `bin/versions/<версия>/`.** Версия по умолчанию остаётся в `bin/opencode` (её же видит `PATH` всех терминалов). Список версий строится по подкаталогам `bin/versions` во время работы — без отдельного файла-реестра.
11. **Версия вкладки через переменную окружения.** Вкладка передаёт свой каталог в `SD_OPENCODE_BIN_DIR`; `OpenCodePathCustomizer` ставит его в начало `PATH` вместо каталога по умолчанию. Поэтому `opencode` после Ctrl-C в вкладке 2.1.4 — это 2.1.4. Альтернатива — запуск по абсолютному пути — работала бы только для первого запуска.
12. **Постоянный адрес вместо `latest`.** `https://opencode.ai/update/api/latest/cli/opencode/opencode-darwin-arm64.zip` перенаправляет на `https://opencode.ai/files/bin/2.1.4/opencode-darwin-arm64.zip`; в сборке — постоянный адрес, иначе SHA-256 сломается на следующем релизе.
13. **2.1.4 во всех шести дистрибутивах.** Сначала только macOS arm64 «для проверки», затем остальные пять платформ. opencode.ai не публикует контрольные суммы, поэтому SHA-256 посчитаны по скачанным архивам; в каждом — один бинарник нужной ОС и архитектуры (проверено `file`, macOS x64 печатает `v2.1.4` через Rosetta). Новая версия добавляется строками в `OPENCODE_EXTRA_VERSIONS`.
