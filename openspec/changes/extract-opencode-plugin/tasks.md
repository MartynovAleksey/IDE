## 1. Каркас плагина

- [x] 1.1 Создать `plugins-external/opencode/` по образцу Hermes: Gradle wrapper, `settings.gradle.kts` (`opencode-intellij`), `build.gradle.kts`, `gradle.properties` (`version=0.1.0`, `ideaPath`), `.gitignore`
- [x] 1.2 `plugin.xml`: id `io.sdopenide.opencode`, имя «OpenCode for IntelliJ», зависимость от `org.jetbrains.plugins.terminal`, `toolWindow id="OpenCode"` (bottom, `/icons/opencode.svg`, `canCloseContents`), `shellExecOptionsCustomizer`
- [x] 1.3 Перенести иконки `opencode.svg` / `opencode_dark.svg` в `src/main/resources/icons/`

## 2. Код плагина

- [x] 2.1 Скопировать `SessionTabs.kt` из Hermes в пакет `io.sdopenide.opencode`
- [x] 2.2 Перенести `OpenCodeToolWindowFactory.kt` и `OpenCodePathCustomizer.kt`; каталог `bin` брать из `pluginPath` плагина `io.sdopenide.opencode`
- [x] 2.3 Скопировать `HermesEscape.kt` как `OpenCodeEscape.kt` и вызывать `keepEscapeInTerminals(toolWindow)` из фабрики панели
- [ ] 2.4 `./gradlew buildPlugin` собирается; `runIde` без бинарников показывает «opencode not found: <путь>»

## 3. Бинарники opencode в zip по ОС

- [x] 3.1 `package_plugin.py`: для 6 платформ скачать `v1.19.5` и `v2.2.5` с GitHub-релизов `anomalyco/opencode` (кеш в `build/`), проверить SHA-256 из спецификации, записать `bin/opencode[.exe]` и `bin/versions/2.2.5/opencode[.exe]` с правами 0755 → `opencode-intellij-0.1.0-<os>.zip`
- [x] 3.2 Проверить, что неверный SHA-256 роняет упаковку и zip не создаётся
- [x] 3.3 Проверить `zipinfo` всех 6 zip: оба бинарника на месте, на Unix-платформах `-rwxr-xr-x`
- [x] 3.4 Добавить короткий README: сборка (`./gradlew buildPlugin`, затем `package_plugin.py`) и установка с диска

## 4. Удаление OpenCode из SD-OpenIDE

- [x] 4.1 Удалить `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/` целиком и иконки `plugins/terminal/resources/icons/opencode{,_dark}.svg`
- [x] 4.2 Убрать из `terminal.xml` `toolWindow id="OpenCode"` и `OpenCodePathCustomizer`
- [x] 4.3 Убрать из `CommunityRepositoryModules.kt` `OPENCODE_*`, `OpencodeExtraVersion`, `extractOpencode` и генераторы в `pluginAuto("intellij.terminal")`, а также ставшие лишними импорты
- [x] 4.4 Вернуть `buildCrossPlatformDistribution = true` в `IdeaCommunityProperties.kt`
- [x] 4.5 Обновить комментарии у `addOrUpdate("OpenCode")` и в `JBTerminalWidget.isTerminalToolWindow`: это хуки для плагина OpenCode for IntelliJ
- [x] 4.6 Проверить, что в SD-OpenIDE не осталось ссылок на удалённое: поиск `opencode` вне `plugins-external/`, `openspec/`, `.ai/`, FUS-словарей
- [x] 4.7 `bazel build` для `intellij.terminal` и build-scripts; `graphify update .`

## 5. Проверка

- [ ] 5.1 macOS arm64, SD-OpenIDE: поставить zip с диска, после перезапуска панель OpenCode стоит между Terminal и Services, вкладка запускает opencode 1.19.5, «+» открывает вторую вкладку
- [ ] 5.2 Стрелка → «opencode 2.2.5»: вкладка «OpenCode 2.2.5», после Ctrl-C `opencode --version` печатает `opencode v2.2.5`; `opencode -s <id>` продолжает сессию
- [ ] 5.3 `opencode --version` в панели Terminal печатает `1.19.5`
- [ ] 5.4 Escape в терминале вкладки уходит в opencode в SD-OpenIDE и в IntelliJ IDEA 2026.2; Escape в поиске терминала закрывает поиск
- [ ] 5.5 Установка в IntelliJ IDEA 2026.2: панель работает так же
- [x] 5.6 Пересобранная SD-OpenIDE без плагина: нет `plugins/terminal/bin/opencode` и панели OpenCode
- [x] 5.7 По подтверждению пользователя: GitHub-релиз `opencode-intellij-0.1.0` с 6 zip; обновить задачу MartynovAleksey/IDE#1
