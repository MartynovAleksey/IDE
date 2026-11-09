## Why

Во вкладках Blackbird в IDE простой Escape до blackbird не доходит, а Ctrl+Esc доходит: терминал IDE отправляет на Ctrl+Esc обычный байт ESC, и blackbird прерывает агента по своей привязке `escape`. Но в строке состояния blackbird написано «esc interrupt», и подсказка в IDE вводит в заблуждение. Логику клавиш blackbird менять не нужно: достаточно, чтобы в IDE подсказка говорила «ctrl+esc», а в обычной консоли оставалась «esc».

## What Changes

- **blackbird (отдельная задача, `blackbird-task.md`).**
  - Новый параметр конфигурации интерфейса `interrupt_hint` (строка) задаёт текст клавиши в подсказке о прерывании: «<interrupt_hint> interrupt», «<interrupt_hint> again to interrupt».
  - По умолчанию значение — `esc`, то есть подсказка как сейчас.
  - Параметр меняет только текст. Привязки клавиш и логика прерывания остаются прежними.
- **Плагин Blackbird for IntelliJ.**
  - В Settings | Tools | Blackbird для каждой версии появляется поле «Interrupt key hint», по умолчанию `ctrl+esc`.
  - Пока поле не пустое, вкладки и терминалы IDE передают значение в `interrupt_hint` через переменную окружения. Конфигурационные файлы пользователя не меняются.
  - Если поле пустое, плагин ничего не передаёт, и blackbird показывает свой текст по умолчанию.
- Пока исправленного blackbird нет, передаваемый параметр ни на что не влияет, и подсказка остаётся «esc interrupt». Прерывание по Ctrl+Esc в IDE работает уже сейчас.

## Capabilities

### New Capabilities
- `blackbird-interrupt-key`: текст подсказки о прерывании в интерфейсе blackbird и его значение по умолчанию во вкладках и терминалах IDE.

### Modified Capabilities
<!-- нет: требования add-blackbird-plugin ещё не перенесены в openspec/specs -->

## Impact

- `plugins-external/blackbird/`: `BlackbirdVersions.kt` (настройка и переменные окружения), `BlackbirdToolWindowFactory.kt`, `BlackbirdPathCustomizer.kt`, `BlackbirdSettingsConfigurable.kt`, тест, README.
- blackbird `blackbird/v1` и `blackbird/v2`: схема конфигурации интерфейса и подсказка в `packages/tui/src/component/prompt/index.tsx`. Затем новые релизы и обновление версий в `package_plugin.py`.
- Плагин OpenCode и SD-OpenIDE не меняются.
