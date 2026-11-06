## Why

blackbird (https://github.com/MartynovAleksey/blackbird) — переименованный форк opencode. У него две линии: `blackbird/v1` на основе opencode 1.19.5 и `blackbird/v2` на основе 2.2.5, а в релизах `v1.19.5.2` и `v2.2.5.2` есть бинарники под шесть платформ. Работать с ним из IDE пока можно только руками в терминале. Плагин OpenCode for IntelliJ (`plugins-external/opencode/`) уже решает ту же задачу для opencode, поэтому blackbird нужен такой же плагин. Отличия: свои имена файлов (`blackbird.json`, `~/.config/blackbird`, `BLACKBIRD_*`) и два флага для перехода с opencode — `--opencode-compatible[=<dir>]` и `--opencode-import [<path>]`.

## What Changes

- Новый плагин **Blackbird for IntelliJ** в `plugins-external/blackbird/`. Это копия плагина opencode, в которой opencode везде заменён на blackbird: id `io.sdopenide.blackbird`, пакет `io.sdopenide.blackbird`, панель **Blackbird**, команда `blackbird`, настройки **Settings | Tools | Blackbird**.
- Бинарники blackbird `1.19.5.2` (по умолчанию) и `2.2.5.2` берутся из GitHub-релизов `MartynovAleksey/blackbird` и проверяются по SHA-256. На каждую ОС собирается отдельный zip: `blackbird-intellij-<версия>-<os>.zip`.
- Всё, что настраивается в плагине opencode, есть и здесь, отдельно для каждой версии: версия по умолчанию, каталог конфигурации (`BLACKBIRD_CONFIG_DIR`), модели в `blackbird.json[c]`, просмотр файла конфигурации.
- **Режим совместимости с opencode** для каждой версии: флажок и необязательный каталог конфигурации opencode. Вкладки запускаются как `blackbird --opencode-compatible`, а каталог opencode, если задан, передаётся в `BLACKBIRD_CONFIG_DIR`: для blackbird это то же, что `--opencode-compatible=<dir>`, но путь не нужно экранировать для разных shell. В терминалах IDE режим включается теми же настройками через переменные окружения. Пока режим включён, настройки моделей и просмотр файла относятся к конфигурации opencode.
- **Импорт настроек opencode** для каждой версии: необязательный путь и кнопка «Import from opencode». Кнопка запускает `blackbird --opencode-import [<path>]` бинарником выбранной версии и показывает отчёт импорта.
- Плагин не бандлится в SD-OpenIDE и SD-OpenIDE не меняется. Escape в панели Blackbird обрабатывает dispatcher самого плагина, как в OpenCode for IntelliJ на обычной IntelliJ IDEA.

## Capabilities

### New Capabilities
- `blackbird-plugin`: плагин Blackbird для IntelliJ. Включает панель с сессиями и выбором версии, `blackbird` в `PATH` терминалов, Escape, настройки по версиям (каталог конфигурации, модели, режим совместимости с opencode, импорт из opencode), бинарники в zip под каждую ОС.

### Modified Capabilities
<!-- нет: плагин opencode и SD-OpenIDE не меняются -->

## Impact

- Новый Gradle-проект `plugins-external/blackbird/`: Kotlin-исходники, `plugin.xml`, иконки, `package_plugin.py`, README.
- Внешняя зависимость — GitHub-релизы `MartynovAleksey/blackbird` (`v1.19.5.2`, `v2.2.5.2`). При выпуске новой версии blackbird нужно обновить версии и SHA-256 в `package_plugin.py`.
- Код плагина opencode копируется, а не выносится в общую библиотеку. Исправления в одном плагине придётся переносить в другой вручную.
