## MODIFIED Requirements

### Requirement: Место кнопки в полосе
В раскладке панелей SD-OpenIDE по умолчанию (новый интерфейс) кнопка OpenCode SHALL стоять в нижней полосе сразу после Terminal и перед Services, если установлен плагин OpenCode for IntelliJ. Без плагина кнопки OpenCode нет.

#### Scenario: Новый каталог настроек
- **WHEN** SD-OpenIDE с установленным плагином OpenCode запускается с чистыми настройками или после Window | Restore Default Layout
- **THEN** в нижней полосе кнопки идут в порядке Terminal, OpenCode, Services

## REMOVED Requirements

### Requirement: Панель OpenCode
**Reason**: панель переезжает в плагин OpenCode for IntelliJ.
**Migration**: поставить плагин; требование — `opencode-plugin` «Панель OpenCode».

### Requirement: Бинарник opencode в дистрибутиве
**Reason**: дистрибутив SD-OpenIDE больше не содержит opencode; бинарники лежат в zip плагина под каждую ОС.
**Migration**: требование — `opencode-plugin` «Бинарники opencode в zip плагина».

### Requirement: Нет бинарника
**Reason**: переезжает в плагин.
**Migration**: требование — `opencode-plugin` «Нет бинарника».

### Requirement: Вкладки сессий
**Reason**: переезжает в плагин.
**Migration**: требование — `opencode-plugin` «Панель OpenCode».

### Requirement: Консоль после выхода из opencode
**Reason**: переезжает в плагин.
**Migration**: требование — `opencode-plugin` «Панель OpenCode».

### Requirement: opencode в PATH
**Reason**: `PATH` теперь дополняет плагин из своего каталога `bin`.
**Migration**: требование — `opencode-plugin` «opencode в PATH».

### Requirement: Escape в opencode
**Reason**: переезжает в плагин; SD-OpenIDE сохраняет id `OpenCode` в `JBTerminalWidget.isTerminalToolWindow`.
**Migration**: требование — `opencode-plugin` «Escape в opencode».

### Requirement: Выбор версии opencode
**Reason**: переезжает в плагин; версии обновлены до `v1.19.5` и `v2.2.5`.
**Migration**: требование — `opencode-plugin` «Выбор версии opencode».
