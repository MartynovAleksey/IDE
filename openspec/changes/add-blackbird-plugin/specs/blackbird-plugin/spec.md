## ADDED Requirements

### Requirement: Плагин Blackbird for IntelliJ
Плагин `io.sdopenide.blackbird` SHALL устанавливаться отдельно в IntelliJ IDEA и SD-OpenIDE 2026.2 (сборка 262) и зависеть от встроенного terminal-плагина. Каждая ОС SHALL получать свой zip `blackbird-intellij-<версия>-<os>.zip` (`macos-arm64`, `macos-x64`, `linux-x64`, `linux-arm64`, `windows-x64`, `windows-arm64`). В zip SHALL входить бинарник blackbird `1.19.5.2` в `bin/` и `2.2.5.2` в `bin/versions/2.2.5.2/`. Оба бинарника SHALL браться из релизов `MartynovAleksey/blackbird` и проверяться по SHA-256.

#### Scenario: Сборка
- **WHEN** выполнены `./gradlew buildPlugin` и `package_plugin.py`
- **THEN** появляются шесть zip, и в каждом есть исполняемые `bin/blackbird[.exe]`, `bin/versions/2.2.5.2/blackbird[.exe]` и `bin/version.txt` с `1.19.5.2`

#### Scenario: Неверная контрольная сумма
- **WHEN** скачанный архив не совпадает с SHA-256 из скрипта
- **THEN** `package_plugin.py` завершается с ошибкой и не пишет zip

### Requirement: Панель Blackbird
Плагин SHALL добавлять панель **Blackbird**. Её вкладки — локальные shell'ы, которые запускают blackbird версии по умолчанию. Кнопка «+» SHALL открывать новую сессию, а стрелка рядом — список версий с пометкой «(default)». Вкладка SHALL оставаться консолью после выхода blackbird. Escape в терминале вкладки SHALL уходить в процесс blackbird. В меню «⋮» панели SHALL быть пункт «Blackbird Settings…».

#### Scenario: Сессия другой версии
- **WHEN** пользователь выбирает в списке у «+» версию `2.2.5.2`, а по умолчанию стоит `1.19.5.2`
- **THEN** открывается вкладка «Blackbird 2.2.5.2», в которой запущен бинарник `2.2.5.2`

#### Scenario: Escape
- **WHEN** в blackbird открыт диалог и пользователь нажимает Escape в терминале вкладки
- **THEN** диалог blackbird закрывается, а фокус остаётся в панели

### Requirement: blackbird в терминалах
Локальные терминалы IDE SHALL получать в начале `PATH` каталог бинарника версии по умолчанию, а также переменные её конфигурации, если они не заданы раньше. В обычном режиме это `BLACKBIRD_CONFIG_DIR`. В режиме совместимости это `BLACKBIRD_OPENCODE_COMPATIBLE=1` и, если каталог opencode задан, `BLACKBIRD_CONFIG_DIR=<каталог opencode>`.

#### Scenario: Терминал
- **WHEN** пользователь открывает вкладку Terminal и выполняет `blackbird --version`
- **THEN** выводится версия по умолчанию из настроек

### Requirement: Настройки по версиям
**Settings | Tools | Blackbird** SHALL задавать версию по умолчанию. Для каждой версии SHALL задаваться:
- каталог конфигурации blackbird;
- флажок режима совместимости с opencode, по умолчанию включённый;
- необязательный каталог конфигурации opencode;
- модели `model` и `small_model` в файле конфигурации активного каталога.

Настройки SHALL показывать файл конфигурации целиком. Кнопки SHALL открывать файл в редакторе и каталог в файловом менеджере. Вкладка версии с каталогом blackbird SHALL получать `BLACKBIRD_CONFIG_DIR`. Вкладка версии 2.x с любым заданным каталогом SHALL запускаться с `--standalone`.

#### Scenario: Свой каталог для версии
- **WHEN** для `2.2.5.2` задан каталог `/cfg/bb2`, а режим совместимости выключен
- **THEN** вкладка этой версии запускает `blackbird --standalone` с `BLACKBIRD_CONFIG_DIR=/cfg/bb2`, а настройки показывают `/cfg/bb2/blackbird.jsonc` или `/cfg/bb2/blackbird.json`

#### Scenario: Модели
- **WHEN** пользователь вводит Default model `local/qwen` и нажимает Apply
- **THEN** в файле конфигурации активного каталога появляется `"model": "local/qwen"`, а комментарии и остальные ключи сохраняются

### Requirement: Режим совместимости с opencode
Если режим совместимости включён, вкладка версии SHALL запускать `blackbird --opencode-compatible`. Вкладка SHALL получать в `BLACKBIRD_CONFIG_DIR` каталог opencode, если он задан, и ничего, если не задан (каталог blackbird SHALL NOT передаваться). Для blackbird это равносильно `--opencode-compatible=<dir>`. Модели и просмотр файла SHALL относиться к каталогу opencode (заданному или `$XDG_CONFIG_HOME/opencode`) и его `opencode.json[c]`.

#### Scenario: Совместимость без каталога
- **WHEN** у версии `1.19.5.2` включён режим совместимости и каталог opencode пуст
- **THEN** вкладка запускает `blackbird --opencode-compatible`, а настройки показывают `~/.config/opencode/opencode.jsonc` или `opencode.json`

#### Scenario: Совместимость со своим каталогом opencode
- **WHEN** у версии `2.2.5.2` включён режим совместимости с каталогом `/cfg/oc`
- **THEN** вкладка запускает `blackbird --opencode-compatible --standalone` с `BLACKBIRD_CONFIG_DIR=/cfg/oc`

#### Scenario: Режим включён по умолчанию
- **WHEN** плагин только что установлен и настройки версии не менялись
- **THEN** вкладка запускает `blackbird --opencode-compatible`, а флажок в настройках отмечен

#### Scenario: Режим выключен
- **WHEN** флажок совместимости снят
- **THEN** вкладка запускается без `--opencode-compatible`, а каталог opencode игнорируется

### Requirement: Импорт настроек opencode
Настройки SHALL содержать для выбранной версии необязательный путь «opencode path» и кнопку «Import from opencode». Кнопка SHALL сразу, без подтверждения, запускать `--opencode-import` бинарником этой версии, с путём, если он задан. Рабочий каталог SHALL быть каталогом проекта. После завершения настройки SHALL показывать вывод команды и перечитывать файл конфигурации.

#### Scenario: Глобальный импорт
- **WHEN** путь пуст и пользователь нажимает «Import from opencode» для `1.19.5.2`
- **THEN** выполняется `bin/blackbird --opencode-import`, и показывается отчёт со строками «Скопировано», «Бэкап», «Пропущено»

#### Scenario: Импорт по пути
- **WHEN** путь `./opencode.json` и выбрана версия `2.2.5.2`
- **THEN** выполняется `bin/versions/2.2.5.2/blackbird --opencode-import ./opencode.json` в каталоге проекта

#### Scenario: Ошибка импорта
- **WHEN** путь не существует и blackbird завершается с ненулевым кодом
- **THEN** показывается окно ошибки с текстом blackbird, настройки не меняются
