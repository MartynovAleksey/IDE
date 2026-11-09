# blackbird-interrupt-key Specification

## Purpose
TBD - created by archiving change blackbird-interrupt-key. Update Purpose after archive.
## Requirements
### Requirement: Текст подсказки о прерывании в blackbird
blackbird SHALL поддерживать в конфигурации интерфейса строковый параметр `interrupt_hint`. В линии `blackbird/v1` это ключ в `tui.json`, в `blackbird/v2` — в `cli.json`. Подсказка в строке состояния промпта SHALL выглядеть как «<interrupt_hint> interrupt» и «<interrupt_hint> again to interrupt». Если параметр не задан или пуст, SHALL использоваться `esc`. Параметр SHALL менять только текст: привязки клавиш и логика прерывания SHALL оставаться прежними.

#### Scenario: Консоль без настроек
- **WHEN** blackbird запущен в обычном терминале без `interrupt_hint`
- **THEN** подсказка — «esc interrupt», и Esc прерывает агента

#### Scenario: Заданный текст
- **WHEN** в конфигурации задано `"interrupt_hint": "ctrl+esc"`
- **THEN** подсказка — «ctrl+esc interrupt», а прерывание по-прежнему срабатывает на `escape`

### Requirement: Текст подсказки по умолчанию в IDE
Settings | Tools | Blackbird SHALL содержать для каждой версии поле «Interrupt key hint», по умолчанию `ctrl+esc`. Пока поле не пустое, вкладки Blackbird этой версии SHALL передавать значение в `interrupt_hint`:
- для 1.x — через `BLACKBIRD_TUI_CONFIG` с путём к файлу, который пишет плагин;
- для 2.x — через `BLACKBIRD_CLI_CONFIG_CONTENT`.

Терминалы IDE SHALL получать то же для версии по умолчанию. Файлы конфигурации пользователя SHALL оставаться без изменений. Если переменная уже задана в окружении, плагин SHALL её не менять. При пустом поле плагин SHALL не передавать ни одну из этих переменных.

#### Scenario: По умолчанию в IDE
- **WHEN** пользователь открывает вкладку Blackbird без изменённых настроек, агент работает
- **THEN** blackbird с поддержкой `interrupt_hint` показывает «ctrl+esc interrupt», а Ctrl+Esc прерывает агента

#### Scenario: Версия 2.x
- **WHEN** открывается вкладка версии 2.x со значением поля `ctrl+esc`
- **THEN** в её окружении есть `BLACKBIRD_CLI_CONFIG_CONTENT={"interrupt_hint":"ctrl+esc"}`, а `BLACKBIRD_TUI_CONFIG` плагин не задаёт

#### Scenario: Пустое поле
- **WHEN** пользователь очищает поле для версии и открывает вкладку этой версии
- **THEN** плагин не передаёт переменных для подсказки, и blackbird показывает «esc interrupt»

#### Scenario: Файлы пользователя
- **WHEN** поле заполнено и пользователь открывает вкладку
- **THEN** `tui.json`, `cli.json` и `blackbird.json` пользователя не меняются

