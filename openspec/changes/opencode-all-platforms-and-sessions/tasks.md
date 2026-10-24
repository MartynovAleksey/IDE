## 1. Сборка всех платформ

- [x] 1.1 В `CommunityRepositoryModules.kt` заменить единственный генератор для macOS arm64 таблицей из шести платформ (архив, SHA-256 из design/spec): `withGeneratedPlatformResources` с проверкой хэша и распаковкой в `bin/`, `withPlatformExecutable(..., "bin/opencode")` для macOS и Linux
- [x] 1.2 В `IdeaCommunityProperties.kt` поставить `buildCrossPlatformDistribution = false`
- [x] 1.3 В `OpenSourceCommunityInstallersBuildTarget.kt` убрать `WINDOWS_ZIP_STEP` из пропускаемых шагов, чтобы собирался Windows `.zip`

## 2. Панель OpenCode

- [x] 2.1 `OpenCodeToolWindowFactory`: вкладка = локальный shell в корне проекта + `sendCommandToExecute("opencode")`; у каждой вкладки свой `Disposable` через `content.setDisposer`; если бинарника нет — прежнее сообщение
- [x] 2.2 Действие «New Session» («+») в заголовке панели (`setTabActions`); имена вкладок `OpenCode`, `OpenCode (2)`, …; при показе панели без вкладок создавать новую
- [x] 2.3 В `terminal.xml` поставить `canCloseContents="true"` для `OpenCode`
- [x] 2.4 Customizer `PATH`: класс `ShellExecOptionsCustomizer`, добавляющий `<terminal plugin>/bin` через `prependEntryToPATH`, если бинарник существует; зарегистрировать в `terminal.xml`
- [x] 2.5 Escape: в `JBTerminalWidget.isTerminalToolWindow()` учитывать id `OpenCode`

## 3. Сборка и проверка

- [x] 3.1 Собрать `./installers.cmd` для всех ОС; проверить, что выпущены `.dmg` (arm64, x64), `.tar.gz` (Linux x64, arm64), `.zip` (Windows x64, arm64; `.exe` на macOS не собирается), а кросс-платформенного архива нет
- [x] 3.2 В каждом артефакте есть `plugins/terminal/bin/opencode[.exe]` нужной ОС и архитектуры (`file`), на macOS/Linux — с правом на исполнение; macOS-бинарники печатают `1.19.4`
- [x] 3.3 Список плагинов в каждом дистрибутиве совпадает с прежним (62)
- [x] 3.4 Запустить macOS arm64 `.app`: «+» открывает вторую вкладку с opencode; закрытие вкладки завершает её процесс; Escape доходит до opencode; после Ctrl-C в вкладке работает shell и `opencode -s <id>`; `opencode --version` работает в Terminal

## 4. Кнопка «+» и выбор версии

- [x] 4.1 «+» через `setTabActions` сразу после вкладок
- [x] 4.2 Сборка: `OPENCODE_EXTRA_VERSIONS` (2.1.4 для macOS arm64) → `bin/versions/2.1.4/opencode`, исполняемый
- [x] 4.3 Стрелка после «+» со списком версий; вкладка «OpenCode <версия>» с `SD_OPENCODE_BIN_DIR`; customizer учитывает переменную
- [x] 4.4 Сборка macOS arm64: в `.app` есть `bin/opencode` и `bin/versions/2.1.4/opencode` (оба `rwx`)
- [x] 4.5 В IDE: «+» и стрелка рядом с вкладкой; «opencode 2.1.4» открывает вкладку с v2.1.4; после Ctrl-C `opencode --version` в ней печатает `v2.1.4`

## 5. opencode 2.1.4 для всех платформ

- [x] 5.1 Скачать архивы 2.1.4 для macOS x64, Linux x64/arm64, Windows x64/arm64, посчитать SHA-256, проверить бинарники (`file`)
- [x] 5.2 Добавить пять платформ в `OPENCODE_EXTRA_VERSIONS`
- [x] 5.3 Собрать все ОС: в каждом артефакте есть `bin/versions/2.1.4/opencode[.exe]` нужной платформы, на macOS/Linux — `rwx`
