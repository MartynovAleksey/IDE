## 1. Упаковка бинарника

- [x] 1.1 Узнать id terminal-плагина (`<id>` в его `plugin.xml`) и убедиться, что `downloadFileToCacheLocation` доступен в модуле build-scripts (без новых зависимостей в `BUILD.bazel`)
- [x] 1.2 В `CommunityRepositoryModules.kt`, в `pluginAuto("intellij.terminal")`, добавить `withGeneratedPlatformResources(MACOS, aarch64, MacLibcImpl.DEFAULT)`: скачать архив v1.19.4, сверить SHA-256 `c400a2f8…b689` (ошибка при несовпадении), извлечь `opencode` в `bin/opencode`
- [x] 1.3 Добавить `withPlatformExecutable(MACOS, aarch64, MacLibcImpl.DEFAULT, "bin/opencode")`

## 2. Панель OpenCode

- [x] 2.1 Добавить иконку `opencode.svg` (16×16) в ресурсы terminal-плагина
- [x] 2.2 Создать `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/OpenCodeToolWindowFactory.kt`: найти `bin/opencode` в каталоге terminal-плагина; если он есть, запустить через `LocalTerminalDirectRunner.createTerminalRunner(project).startShellTerminalWidget(toolWindow.disposable, ShellStartupOptions(shellCommand=[путь], workingDirectory=корень проекта), true)` и показать `widget.component`; если нет — показать `JBLabel` «opencode not found: <путь>»
- [x] 2.3 Зарегистрировать `<toolWindow id="OpenCode" anchor="bottom" icon=… factoryClass=…OpenCodeToolWindowFactory/>` в `plugins/terminal/resources/META-INF/terminal.xml`
- [x] 2.4 В `defaultToolWindowlayoutProvider.kt`, `addPlatformDefaultsV2()`, вставить `addOrUpdate("OpenCode")` между `"Terminal"` и `"Services"`

## 3. Сборка и проверка

- [x] 3.1 Собрать `./installers.cmd -Dintellij.build.target.os=mac -Dintellij.build.target.arch=aarch64`
- [x] 3.2 В `.sit` есть `SD-OpenIDE.app/Contents/plugins/terminal/bin/opencode` с правом на исполнение; извлечённый файл печатает `1.19.4` на `--version`
- [x] 3.3 Список плагинов совпадает с прошлой сборкой (62)
- [x] 3.4 Запустить `.app` с проектом: кнопка OpenCode стоит между Terminal и Services, при открытии панели работает интерфейс opencode в корне проекта, ввод с клавиатуры доходит
- [ ] 3.5 Выполнить `graphify update .`
