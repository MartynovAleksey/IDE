## Context

- Сейчас OpenCode — часть SD-OpenIDE:
  - `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/`: `OpenCodeToolWindowFactory.kt` (панель, «+», стрелка версий), `OpenCodePathCustomizer.kt` (PATH), `AgentSessionTabs.kt` (вкладки, `terminalPluginDir()`; используется только OpenCode);
  - регистрации в `terminal.xml`;
  - иконки `opencode{,_dark}.svg`;
  - в `CommunityRepositoryModules.kt`: `OPENCODE_VERSION`, `OPENCODE_ARCHIVES`, `OPENCODE_EXTRA_VERSIONS` и `extractOpencode`, которые кладут `bin/opencode` и `bin/versions/<v>/opencode` в terminal-плагин по платформам;
  - в `IdeaCommunityProperties.kt` стоит `buildCrossPlatformDistribution = false` из-за бинарника; в `idea/2026.2.3` было `true`.
- Образец — `plugins-external/hermes/`:
  - Gradle-проект на IntelliJ Platform Gradle Plugin 2.19, `local(ideaPath)`, `bundledPlugin("org.jetbrains.plugins.terminal")`, 262–262.*;
  - копия логики вкладок `SessionTabs.kt`;
  - `HermesEscape.kt` — dispatcher Escape;
  - `payload/package_plugin.py` дописывает payload в zip от `buildPlugin`, сохраняя Unix-права.
- Хуки платформы в SD-OpenIDE: `addOrUpdate("OpenCode")` в раскладке и `"OpenCode"` в `JBTerminalWidget.isTerminalToolWindow`. Они остаются.
- `v1.19.5` и `v2.2.5` есть в GitHub-релизах `anomalyco/opencode` для всех 6 платформ; SHA-256 взяты из поля `digest` ассетов релиза. Раньше 2.x брался с `opencode.ai/files/bin/` — теперь оба из одного источника.

## Goals / Non-Goals

**Goals:**
- Отдельный плагин OpenCode for IntelliJ, ставится в IntelliJ IDEA и SD-OpenIDE 262.
- Поведение как сейчас: вкладки, «+», стрелка версий, консоль после выхода, PATH, Escape, «opencode not found».
- opencode `v1.19.5` по умолчанию и `v2.2.5` в списке; 6 zip, по одному на ОС.
- SD-OpenIDE без OpenCode, с кроссплатформенным дистрибутивом как в upstream.

**Non-Goals:**
- Бандлинг плагина в SD-OpenIDE и публикация в JetBrains Marketplace.
- Страница настроек OpenCode: её не было, конфиг opencode — его собственный.
- Автообновление opencode из плагина и скачивание бинарников в рантайме.
- Подпись плагина.
- `.ai/render-guides.mjs` / `opencode.json`: это конфиг для разработки репозитория.

## Decisions

1. **Gradle-проект `plugins-external/opencode/`** — копия каркаса Hermes (wrapper, `settings.gradle.kts` с `rootProject.name = "opencode-intellij"`, `build.gradle.kts`, `gradle.properties` с `ideaPath`, `.gitignore`). Плагин отдельный от Bazel-сборки, как Hermes. Альтернатива — модуль монорепозитория в Bazel; отклонена: Hermes уже задал образец, и плагин нельзя будет вынести в свой репозиторий.
2. **Код переезжает в пакет `io.sdopenide.opencode`** почти без изменений. `terminalPluginDir()` заменяется на `PluginManagerCore.getPlugin(PluginId.getId("io.sdopenide.opencode"))?.pluginPath`. Логика вкладок — копия `SessionTabs.kt` из Hermes; общий модуль для двух внешних плагинов не делаем: это 70 строк, а общая библиотека усложнит сборку обоих. Id панели остаётся `OpenCode`, иначе перестанут работать хуки SD-OpenIDE. Раскладка каталогов та же: `bin/opencode[.exe]` и `bin/versions/<v>/opencode[.exe]`, поэтому код выбора версий не меняется. Env-переменная `SD_OPENCODE_BIN_DIR` сохраняется.
3. **Упаковка по ОС — `plugins-external/opencode/package_plugin.py <opencode-intellij-<ver>.zip> <out-dir>`**. Скрипт:
   - для каждой из 6 платформ скачивает два архива с GitHub (с кешем в `build/opencode-archives/`);
   - проверяет SHA-256 по таблице в самом скрипте;
   - достаёт из архива бинарник `opencode[.exe]`;
   - пишет его в копию zip плагина как `opencode-intellij/bin/...` с `external_attr` 0755 и `create_system = 3`.

   Результат — `<out-dir>/opencode-intellij-<ver>-<os>.zip`. Python, а не Gradle `Zip`, по той же причине, что у Hermes: нужен один скрипт для всех ОС с явными Unix-правами. Кросс-сборка на Mac допустима: бинарники готовые, ничего не запускается. Проверить `--version` можно только для ОС хоста. GitHub Actions, как у Hermes, не нужен.
4. **Escape**: копия `HermesEscape.kt` (`keepEscapeInTerminals(toolWindow)`) вызывается из `OpenCodeToolWindowFactory`. В SD-OpenIDE она дублирует платформенную обработку без вреда, в IntelliJ IDEA без неё Escape уводит фокус в редактор.
5. **Удаление из SD-OpenIDE**:
   - удаляются каталог `plugins/terminal/src/org/jetbrains/plugins/terminal/opencode/` целиком (вместе с `AgentSessionTabs.kt`, у которого не останется пользователей), записи `toolWindow`/`shellExecOptionsCustomizer` в `terminal.xml`, иконки, OpenCode-константы и генераторы в `CommunityRepositoryModules.kt`;
   - `buildCrossPlatformDistribution = true`;
   - остаются `addOrUpdate("OpenCode")` и `"OpenCode"` в `JBTerminalWidget` — комментарии к ним правятся: теперь это хуки для плагина.
6. **Версия плагина `0.1.0`**, релиз `opencode-intellij-0.1.0` на GitHub публикуется только по подтверждению пользователя.

## Risks / Trade-offs

- [SD-OpenIDE после обновления без OpenCode] → в описании релиза IDE дать ссылку на плагин; спецификация явно запрещает бандлинг (решение пользователя).
- [SHA-256 из `digest` GitHub — значение, которое считает сам GitHub] → скрипт сверяет его со скачанным файлом; при упаковке однократно сверить хеш хотя бы одного архива вручную (`shasum -a 256`).
- [`bin/` Windows-архивов могут содержать не только `opencode.exe`] → извлекается только бинарник по имени; если в новом релизе понадобятся соседние файлы, проверка на Windows это покажет.
- [Проверка на Linux/Windows с Mac невозможна] → на Mac проверяется macOS arm64 целиком; для остальных — содержимое zip и права (`zipinfo`).
- [Две копии логики вкладок и Escape в Hermes и OpenCode] → принято, как и для Hermes; при третьем агенте вынести в общий модуль.

## Migration Plan

1. Собрать плагин и zip по ОС, проверить в SD-OpenIDE и IntelliJ IDEA на macOS arm64.
2. Удалить OpenCode из SD-OpenIDE, пересобрать IDE, убедиться, что плагин в ней работает как родной (место в полосе, Escape).
3. Откат: revert коммита удаления; плагин можно просто не ставить.

## Open Questions

- Нет.
