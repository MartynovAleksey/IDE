## MODIFIED Requirements

### Requirement: Панель OpenCode
IDE SHALL иметь панель (tool window) с id `OpenCode` в нижней полосе. При первом открытии в проекте панель SHALL открывать вкладку-сессию: локальный shell в терминальном виджете, том же, что использует панель Terminal, с рабочим каталогом в корне проекта. В этом shell SHALL сразу выполняться команда `opencode`.

#### Scenario: Открытие панели
- **WHEN** пользователь нажимает кнопку OpenCode в открытом проекте
- **THEN** открывается панель с вкладкой, в которой работает интерфейс opencode, запущенный в корне проекта

#### Scenario: Ввод с клавиатуры
- **WHEN** пользователь печатает в панели OpenCode
- **THEN** ввод попадает в opencode так же, как в процесс в панели Terminal

### Requirement: Бинарник opencode в дистрибутиве
Каждый дистрибутив SD-OpenIDE SHALL содержать бинарник opencode v1.19.4 для своей платформы в каталоге terminal-плагина `bin/` (`opencode`, на Windows `opencode.exe`), на Unix — с правом на исполнение. Архивы берутся с `https://github.com/anomalyco/opencode/releases/download/v1.19.4/`:

| Дистрибутив | Архив | SHA-256 |
|---|---|---|
| macOS arm64 | `opencode-darwin-arm64.zip` | `c400a2f8c8ab7e99478a7f4dd1a466b57bf33888e520d85c55780a5464c4b689` |
| macOS x64 | `opencode-darwin-x64.zip` | `123ebd72609c4f8dbae0485cdac39b9aedfeb6576b2eef64f2e73152b1de82f4` |
| Linux x64 | `opencode-linux-x64.tar.gz` | `849bd6b3d959a1d2ef200ed1634a51c79a45ca236aa26665d75debd694805cce` |
| Linux arm64 | `opencode-linux-arm64.tar.gz` | `8568230ccd847dc93e95b9f6894379077d3e014a16b48040743573e77c23e85f` |
| Windows x64 | `opencode-windows-x64.zip` | `93b899277bd6f68803009e32bf71772ce43c34b18adf1e93b9b4d8780fdf62e5` |
| Windows arm64 | `opencode-windows-arm64.zip` | `36e696fd1d275b469ab02a1ccc00d161bfdcc0a4ffa93c2b7a4e57399f3ff347` |

Сборка MUST проверять SHA-256 каждого архива и падать при несовпадении.

#### Scenario: Сборка всех дистрибутивов
- **WHEN** выполняется `./installers.cmd` без ограничения ОС и архитектуры
- **THEN** выпускаются артефакты для macOS arm64 и x64 (`.dmg`), Linux x64 и arm64 (`.tar.gz`), Windows x64 и arm64 (`.zip`; `.exe` — только при сборке не на macOS), и в каждом есть `plugins/terminal/bin/opencode[.exe]` своей платформы

#### Scenario: Сборка .dmg
- **WHEN** выполняется `./installers.cmd -Dintellij.build.target.os=mac -Dintellij.build.target.arch=aarch64`
- **THEN** в `.app` из `.dmg` есть исполняемый `Contents/plugins/terminal/bin/opencode`, и `opencode --version` печатает `1.19.4`

#### Scenario: Подменённый архив
- **WHEN** SHA-256 скачанного архива не совпадает со значением из таблицы
- **THEN** сборка завершается ошибкой и не кладёт бинарник в дистрибутив

## ADDED Requirements

### Requirement: Вкладки сессий
Панель OpenCode SHALL поддерживать несколько вкладок-сессий, как панель Terminal: в заголовке панели есть действие «New Session» («+»), каждая вкладка закрывается отдельно, и закрытие вкладки MUST завершать её процесс. Если закрыта последняя вкладка, следующее открытие панели SHALL создавать новую.

#### Scenario: Новая сессия
- **WHEN** пользователь нажимает «+» в заголовке панели OpenCode
- **THEN** появляется новая вкладка с отдельно запущенным opencode, а остальные вкладки продолжают работать

#### Scenario: Закрытие вкладки
- **WHEN** пользователь закрывает вкладку
- **THEN** её процесс завершается, остальные вкладки не затронуты

### Requirement: Консоль после выхода из opencode
После выхода из opencode (например, по Ctrl-C) вкладка MUST оставаться рабочей консолью: shell, из которого был запущен opencode, продолжает принимать команды.

#### Scenario: Продолжение сессии
- **WHEN** пользователь выходит из opencode по Ctrl-C и вводит `opencode -s <id сессии>`
- **THEN** в той же вкладке запускается opencode с указанной сессией, перезапуск IDE не нужен

### Requirement: opencode в PATH
Каталог terminal-плагина `bin/` с встроенным opencode SHALL добавляться в начало `PATH` локальных терминальных сессий IDE, если бинарник существует.

#### Scenario: Команда в Terminal
- **WHEN** пользователь вводит `opencode --version` в панели Terminal или OpenCode
- **THEN** выполняется встроенный opencode и печатает `1.19.4`

### Requirement: Escape в opencode
Нажатие Escape в панели OpenCode MUST передаваться в процесс вкладки и MUST NOT переводить фокус в редактор. Перевод фокуса работает так же, как в панели Terminal: по шорткату действия `Terminal.SwitchFocusToEditor`, если он назначен.

#### Scenario: Escape в интерфейсе opencode
- **WHEN** фокус в панели OpenCode и пользователь нажимает Escape
- **THEN** opencode получает Escape (например, закрывает свой диалог), а фокус остаётся в панели

### Requirement: Выбор версии opencode
Сразу после «+» в панели OpenCode SHALL быть кнопка-стрелка, как в Terminal, со списком встроенных версий opencode: «opencode (default)» и каждая дополнительная версия из `bin/versions/<версия>/` terminal-плагина. Выбор пункта MUST открывать новую вкладку с этой версией; в такой вкладке и после выхода из opencode команда `opencode` MUST запускать ту же версию. Дополнительные версии скачиваются при сборке с постоянного адреса и проверяются по SHA-256; сейчас это `2.1.4` во всех дистрибутивах, с `https://opencode.ai/files/bin/2.1.4/`:

| Дистрибутив | Архив | SHA-256 |
|---|---|---|
| macOS arm64 | `opencode-darwin-arm64.zip` | `f9edc1dded080e39aa61db94270a51af66772ac572be7d6b15fa6f87251a3dff` |
| macOS x64 | `opencode-darwin-x64.zip` | `7f35265b6900ce75b01e03cab2497622026b402c021ecf6e6e68755874245a6b` |
| Linux x64 | `opencode-linux-x64.tar.gz` | `2edaf8cf96e81dec5ee996a3196b03345cf59e5a1673d83a4599bb4afc5c5f6d` |
| Linux arm64 | `opencode-linux-arm64.tar.gz` | `759c9d6e7b246b81bcc93dfc6cc3bb2a38287c75b6202e305eacacb5dd882315` |
| Windows x64 | `opencode-windows-x64.zip` | `6d3ee895c0f6e089bb1f1ee3addfa3d700cc4488a7da97f182cb700d3985a799` |
| Windows arm64 | `opencode-windows-arm64.zip` | `2bf735889e52a817512d57dfe23a03d3861de0da2f2ef398ef9e91d8a4e81a20` |

#### Scenario: Вкладка с версией 2.1.4
- **WHEN** пользователь нажимает стрелку после «+» и выбирает «opencode 2.1.4»
- **THEN** открывается вкладка «OpenCode 2.1.4», в ней работает opencode v2.1.4, а после Ctrl-C `opencode --version` в этой вкладке печатает `v2.1.4`

#### Scenario: Кнопка рядом с вкладками
- **WHEN** открыта панель OpenCode
- **THEN** «+» и стрелка стоят сразу после последней вкладки, а не в правой части заголовка
