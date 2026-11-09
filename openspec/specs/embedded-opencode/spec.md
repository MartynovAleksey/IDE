# embedded-opencode Specification

## Purpose
Встроенный AI-агент opencode в SD-OpenIDE: панель OpenCode рядом с Terminal с вкладками-сессиями и выбором версии, бинарники opencode в дистрибутивах всех платформ.
## Requirements
### Requirement: Место кнопки в полосе
В раскладке панелей SD-OpenIDE по умолчанию (новый интерфейс) кнопка OpenCode SHALL стоять в нижней полосе сразу после Terminal и перед Services, если установлен плагин OpenCode for IntelliJ. Без плагина кнопки OpenCode нет.

#### Scenario: Новый каталог настроек
- **WHEN** SD-OpenIDE с установленным плагином OpenCode запускается с чистыми настройками или после Window | Restore Default Layout
- **THEN** в нижней полосе кнопки идут в порядке Terminal, OpenCode, Services

