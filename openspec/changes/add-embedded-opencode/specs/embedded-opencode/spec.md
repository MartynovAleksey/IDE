## ADDED Requirements

### Requirement: Панель OpenCode
IDE SHALL иметь панель (tool window) с id `OpenCode` в нижней полосе. При первом открытии в проекте панель SHALL запускать встроенный бинарник opencode в терминальном виджете, том же, что использует панель Terminal, с рабочим каталогом в корне проекта.

#### Scenario: Открытие панели
- **WHEN** пользователь нажимает кнопку OpenCode в открытом проекте
- **THEN** открывается панель, в которой работает интерфейс opencode, запущенный в корне проекта

#### Scenario: Ввод с клавиатуры
- **WHEN** пользователь печатает в панели OpenCode
- **THEN** ввод попадает в opencode так же, как в процесс в панели Terminal

### Requirement: Место кнопки в полосе
В раскладке панелей по умолчанию (новый интерфейс) кнопка OpenCode SHALL стоять в нижней полосе сразу после Terminal и перед Services.

#### Scenario: Новый каталог настроек
- **WHEN** SD-OpenIDE запускается с чистыми настройками или после Window | Restore Default Layout
- **THEN** в нижней полосе кнопки идут в порядке Terminal, OpenCode, Services

### Requirement: Бинарник opencode в дистрибутиве
Сборка для macOS arm64 SHALL класть бинарник opencode v1.19.4 из `https://github.com/anomalyco/opencode/releases/download/v1.19.4/opencode-darwin-arm64.zip` в `SD-OpenIDE.app/Contents/plugins/terminal/bin/opencode` с правом на исполнение. Сборка MUST проверять SHA-256 архива и падать при несовпадении.

#### Scenario: Сборка .dmg
- **WHEN** выполняется `./installers.cmd -Dintellij.build.target.os=mac -Dintellij.build.target.arch=aarch64`
- **THEN** в `.app` из `.dmg` есть исполняемый `Contents/plugins/terminal/bin/opencode`, и `opencode --version` печатает `1.19.4`

#### Scenario: Подменённый архив
- **WHEN** SHA-256 скачанного архива не равен `c400a2f8c8ab7e99478a7f4dd1a466b57bf33888e520d85c55780a5464c4b689`
- **THEN** сборка завершается ошибкой и не кладёт бинарник в дистрибутив

### Requirement: Нет бинарника
Если бинарник opencode не найден (например, IDE запущена из исходников), панель OpenCode MUST NOT падать. Она SHALL показывать сообщение «opencode not found» с ожидаемым путём (интерфейс IDE английский).

#### Scenario: Запуск из исходников
- **WHEN** пользователь открывает панель OpenCode в сборке без `bin/opencode`
- **THEN** панель показывает текст «opencode not found: <ожидаемый путь>», и ошибок в IDE нет
