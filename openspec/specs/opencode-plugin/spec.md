# opencode-plugin Specification

## Purpose
TBD - created by archiving change extract-opencode-plugin. Update Purpose after archive.
## Requirements
### Requirement: Плагин OpenCode
OpenCode SHALL поставляться отдельным плагином IntelliJ «OpenCode for IntelliJ» (id `io.sdopenide.opencode`). Плагин совместим с IntelliJ IDEA и SD-OpenIDE сборки 262 и зависит от встроенного terminal-плагина. SD-OpenIDE MUST NOT бандлить этот плагин и MUST NOT содержать opencode без него: плагин ставится отдельно, с диска или из GitHub-релиза `opencode-intellij-<версия>`.

#### Scenario: Установка в IntelliJ IDEA
- **WHEN** пользователь ставит `opencode-intellij-<версия>-<ОС>.zip` через Settings | Plugins | Install Plugin from Disk в IntelliJ IDEA 2026.2
- **THEN** после перезапуска в нижней полосе есть панель OpenCode

#### Scenario: SD-OpenIDE без плагина
- **WHEN** собирается дистрибутив SD-OpenIDE
- **THEN** в нём нет `plugins/terminal/bin/opencode[.exe]`, `plugins/terminal/bin/versions/` и панели OpenCode

### Requirement: Бинарники opencode в zip плагина
Zip плагина SHALL выпускаться отдельно для каждой ОС: macOS arm64 и x64, Linux x64 и arm64, Windows x64 и arm64. Каждый zip SHALL содержать opencode своей платформы в двух версиях:
- `v1.19.5` по умолчанию — в `<плагин>/bin/opencode[.exe]`;
- `v2.2.5` для выбора — в `<плагин>/bin/versions/2.2.5/opencode[.exe]`.

На Unix бинарники MUST быть исполняемыми. Архивы берутся из `https://github.com/anomalyco/opencode/releases/download/<тег>/`. Сборка MUST проверять SHA-256 каждого архива и падать при несовпадении.

| Платформа | Архив | SHA-256 `v1.19.5` | SHA-256 `v2.2.5` |
|---|---|---|---|
| macOS arm64 | `opencode-darwin-arm64.zip` | `c1c9e62521192192ef4c3ed1355ada2144055bddb61682217e997b03c55203c1` | `25ba9908979fa7d764792c82589b4b5768ab1511154e0317d98aa77d216ce767` |
| macOS x64 | `opencode-darwin-x64.zip` | `b0212efa6db347187225a17fa3a31c6b261bf4c9f1589a290b0ca902d720d250` | `4d4419022e6ffbd48afc97c5101bdf001d38b0499f516169b9a80a87a340fd56` |
| Linux x64 | `opencode-linux-x64.tar.gz` | `588310a336fa3b9df36b3f0225b26f679d453efaad0cf3d66c3651ef3ec1647a` | `09f44302c10fd036912f59c81fd1aef5df45dd06f649197f9dfc9bbe2a79614e` |
| Linux arm64 | `opencode-linux-arm64.tar.gz` | `fae5be262d693b7c5e562856cc473e59e741772936db0c0600ac0d5ed5bd088b` | `5a528f42caf576c6cdece6a191eb273d3db54effa5518e0402684d8f8026aeb6` |
| Windows x64 | `opencode-windows-x64.zip` | `09a6e693157f0887e504f2cf4357a8cbddc5ead5d0dffb9a269dcc47c48640f5` | `2f3bfd6b3527c60c2a882557b20ddc9ba6bdff4b5a8fe61c853da8fcfb3076e8` |
| Windows arm64 | `opencode-windows-arm64.zip` | `5d49139bd917511ff35a3c06065c9f47918346668f05ea020e778286f302fbb7` | `4e426a39d68516b036cd28b574c5ce5f3b50846ab460521f83f2458fe7f242d8` |

#### Scenario: Сборка всех zip
- **WHEN** выполняется сборка плагина и упаковка по ОС
- **THEN** появляются 6 файлов `opencode-intellij-<версия>-<ОС>.zip`, в каждом `bin/opencode[.exe]` и `bin/versions/2.2.5/opencode[.exe]` своей платформы

#### Scenario: Права на исполнение
- **WHEN** плагин установлен на macOS или Linux
- **THEN** `<плагин>/bin/opencode --version` печатает `1.19.5`, а `<плагин>/bin/versions/2.2.5/opencode --version` — `opencode v2.2.5`

#### Scenario: Подменённый архив
- **WHEN** SHA-256 скачанного архива не совпадает со значением из таблицы
- **THEN** упаковка завершается ошибкой, zip для этой ОС не создаётся

### Requirement: Панель OpenCode
Плагин SHALL давать панель (tool window) с id `OpenCode` в нижней полосе. Каждая вкладка-сессия — это локальный shell в терминальном виджете terminal-плагина с рабочим каталогом в корне проекта; в нём сразу выполняется `opencode`. При первом открытии панели в проекте и после закрытия последней вкладки открытие панели SHALL создавать новую вкладку. Действие «New Session» («+») SHALL стоять сразу после последней вкладки и открывать ещё одну вкладку. Закрытие вкладки MUST завершать её процесс. После выхода из opencode вкладка MUST оставаться рабочей консолью.

#### Scenario: Открытие панели
- **WHEN** пользователь нажимает кнопку OpenCode в открытом проекте
- **THEN** открывается панель с вкладкой, в которой работает opencode `1.19.5`, запущенный в корне проекта

#### Scenario: Новая сессия
- **WHEN** пользователь нажимает «+»
- **THEN** появляется новая вкладка с отдельно запущенным opencode, остальные вкладки продолжают работать

#### Scenario: Продолжение сессии
- **WHEN** пользователь выходит из opencode по Ctrl-C и вводит `opencode -s <id сессии>`
- **THEN** в той же вкладке запускается opencode с указанной сессией

### Requirement: Выбор версии opencode
Сразу после «+» SHALL стоять кнопка-стрелка со списком версий: «opencode (default)» и каждая версия из `<плагин>/bin/versions/<версия>/`. Выбор пункта MUST открывать новую вкладку с этой версией. В такой вкладке, в том числе после выхода из opencode, команда `opencode` MUST запускать ту же версию.

#### Scenario: Вкладка с версией 2.2.5
- **WHEN** пользователь нажимает стрелку после «+» и выбирает «opencode 2.2.5»
- **THEN** открывается вкладка «OpenCode 2.2.5» с opencode v2.2.5, а после Ctrl-C `opencode --version` в ней печатает `opencode v2.2.5`

### Requirement: Нет бинарника
Если бинарник opencode не найден (например, плагин запущен через `runIde` без упаковки по ОС), панель OpenCode MUST NOT падать. Она SHALL показывать сообщение «opencode not found: <ожидаемый путь>».

#### Scenario: Плагин без бинарников
- **WHEN** пользователь открывает панель OpenCode в плагине без `bin/opencode`
- **THEN** панель показывает «opencode not found: <путь>», ошибок в IDE нет

### Requirement: opencode в PATH
Каталог `<плагин>/bin` SHALL добавляться в начало `PATH` локальных терминальных сессий IDE, если в нём есть бинарник opencode. Во вкладке с выбранной версией первым в `PATH` SHALL стоять каталог этой версии.

#### Scenario: Команда в Terminal
- **WHEN** пользователь вводит `opencode --version` в панели Terminal
- **THEN** выполняется opencode из плагина и печатает `1.19.5`

### Requirement: Escape в opencode
Escape без модификаторов в терминале вкладки OpenCode MUST передаваться процессу вкладки и MUST NOT переводить фокус в редактор — и в SD-OpenIDE, и в IntelliJ IDEA. Escape в поле поиска терминала SHALL по-прежнему закрывать поиск.

#### Scenario: Escape в IntelliJ IDEA
- **WHEN** фокус в терминале вкладки OpenCode в IntelliJ IDEA и пользователь нажимает Escape
- **THEN** opencode получает Escape, фокус остаётся в панели

