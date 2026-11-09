## 1. Плагин: настройка и окружение

- [x] 1.1 В `BlackbirdVersions.kt` добавить `interruptHint(version)` / `setInterruptHint(version, value)` (`sd.blackbird.interruptHint.<версия>`, по умолчанию `ctrl+esc`) и `interruptHintEnv(version): Map<String, String>`: для 1.x — `BLACKBIRD_TUI_CONFIG` с путём к файлу `{"interrupt_hint": ...}` в системном каталоге IDE (перезаписывать только при изменении); для 2.x — `BLACKBIRD_CLI_CONFIG_CONTENT`; при пустом значении — пусто
- [x] 1.2 `BlackbirdToolWindowFactory.addSession`: добавлять `interruptHintEnv` в окружение вкладки
- [x] 1.3 `BlackbirdPathCustomizer`: для версии по умолчанию добавлять те же переменные, если они ещё не заданы
- [x] 1.4 Тест на `interruptHintEnv`: 1.x, 2.x, пустое значение, экранирование кавычек в JSON

## 2. Плагин: форма настроек

- [x] 2.1 В `BlackbirdSettingsConfigurable` добавить в блок версии поле «Interrupt key hint» с подсказкой, что оно меняет только текст в blackbird; учесть его в `isModified`, `apply`, `reset` и при смене версии
- [x] 2.2 README: описать поле

## 3. blackbird

- [x] 3.1 Передать задачу `blackbird-task.md` агенту или в `MartynovAleksey/blackbird` (ветки `blackbird/v1` и `blackbird/v2`)
- [x] 3.2 После релизов blackbird с `interrupt_hint` обновить версии и SHA-256 в `package_plugin.py`

## 4. Проверка и выпуск

- [ ] 4.1 `./gradlew test buildPlugin`; вручную: вкладки 1.x и 2.x показывают «ctrl+esc interrupt» (с новым blackbird), Ctrl+Esc прерывает; с пустым полем — «esc interrupt»
- [ ] 4.2 Выпустить новую версию плагина
