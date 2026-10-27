## ADDED Requirements

### Requirement: Плагин Hermes
Hermes SHALL поставляться отдельным плагином IntelliJ «Hermes for IntelliJ», совместимым с IntelliJ IDEA и SD-OpenIDE сборки 262 и зависящим от встроенного terminal-плагина. SD-OpenIDE MUST NOT содержать Hermes без установленного плагина.

#### Scenario: Установка в IntelliJ IDEA
- **WHEN** пользователь ставит `hermes-intellij-<версия>-<ОС>.zip` через Settings | Plugins | Install Plugin from Disk в IntelliJ IDEA 2026.2
- **THEN** после перезапуска есть панель Hermes и страница Settings | Tools | Hermes

#### Scenario: SD-OpenIDE без плагина
- **WHEN** SD-OpenIDE собрана без плагина
- **THEN** в дистрибутиве нет `plugins/terminal/hermes/` и панели Hermes

### Requirement: Полноценный Hermes в zip плагина
Zip плагина для каждой из платформ macOS arm64, Linux x64, Linux arm64, Windows x64 SHALL содержать переносимый Hermes v0.22.5 (свой CPython 3.14, зависимости по `uv.lock`, исходники, лицензия, launcher) и инструменты из `pm/lock.json` Hermes как минимум `ripgrep`, `node`, `npm`, `ffmpeg` для своей платформы, проверенные по SHA-256 из этого lock. Hermes из плагина MUST NOT сообщать `install out of sync` из-за этих инструментов.

#### Scenario: Проверка установки
- **WHEN** во вкладке Hermes выполняется команда проверки установки Hermes (`hermes doctor` / статус)
- **THEN** нет предупреждения `install out of sync` про `ffmpeg`, `node`, `npm`, `ripgrep`

#### Scenario: Права на исполнение
- **WHEN** плагин установлен на macOS или Linux
- **THEN** launcher, Python и инструменты Hermes исполняемые, симлинки `npm`/`npx` на месте (их сохраняет zip, установщик плагинов восстанавливает)

### Requirement: Панель и настройки Hermes
Плагин SHALL давать панель `Hermes` с вкладками-консолями (локальный shell в корне проекта, в нём сразу `hermes`; «+» после вкладок; закрытие вкладки завершает процесс; консоль после выхода), страницу Settings | Tools | Hermes (модель `model.default`, провайдер `model.provider`, Base URL `model.base_url`, интерфейс `display.interface`, кнопки «Run hermes setup…», «Select model…», «Open config.yaml», «Open .env»; чтение/запись средствами Hermes), пункт «Hermes Settings…» в меню «⋮» панели и флаг «Run hermes setup in new Hermes tabs while no model is selected», по умолчанию выключенный. Каталог `bin` Hermes SHALL добавляться в `PATH` локальных терминалов.

#### Scenario: Вкладка по умолчанию
- **WHEN** модель не выбрана, флаг не менялся, и пользователь открывает панель Hermes
- **THEN** во вкладке запускается `hermes`

#### Scenario: Смена модели
- **WHEN** пользователь вводит модель на странице Hermes и нажимает OK
- **THEN** `hermes config get model.default` возвращает введённое значение

### Requirement: Escape в Hermes
Нажатие Escape во вкладке Hermes MUST передаваться процессу Hermes и MUST NOT переводить фокус в редактор — и в SD-OpenIDE, и в IntelliJ IDEA.

#### Scenario: Escape в IntelliJ IDEA
- **WHEN** фокус во вкладке Hermes в IntelliJ IDEA и пользователь нажимает Escape
- **THEN** Hermes получает Escape, фокус остаётся в панели
