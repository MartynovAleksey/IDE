## 1. Копия плагина

- [x] 1.1 Скопировать `plugins-external/opencode/` в `plugins-external/blackbird/` без `build/`, `.gradle/` и `.idea/`
- [x] 1.2 Заменить opencode на blackbird в пакете, классах, `plugin.xml` (id, name, description, tool window `Blackbird`, configurable `sd.blackbird`), ключах `sd.blackbird.*`, `SD_BLACKBIRD_BIN_DIR`, `rootProject.name`, `version=0.1.0`, именах бинарника `blackbird[.exe]` и `bin/versions/`
- [x] 1.3 Иконки `blackbird.svg` и `blackbird_dark.svg`
- [x] 1.4 README: установка, версии, настройки, режим совместимости, импорт

## 2. Бинарники

- [x] 2.1 `package_plugin.py`: версии `1.19.5.2` (по умолчанию) и `2.2.5.2`, URL релизов `MartynovAleksey/blackbird`, имена архивов `blackbird-v<версия>-<darwin|linux|windows>-<arch>.<tar.gz|zip>`, SHA-256 из `SHA256SUMS`
- [x] 2.2 Искать `blackbird[.exe]` по имени в любом каталоге архива и падать, если его нет
- [x] 2.3 Собрать zip под все шесть ОС и проверить в каждом бинарники, права 0755 и `bin/version.txt`

## 3. Конфигурация по версиям

- [x] 3.1 В `BlackbirdVersions.kt`: глобальные каталоги blackbird и opencode с учётом `XDG_CONFIG_HOME`; файл конфигурации `blackbird.jsonc|blackbird.json|config.json` или `opencode.jsonc|opencode.json|config.json`; хранение `compat` и `opencodeDir` для каждой версии
- [x] 3.2 Команда и окружение вкладки по таблице D3, включая `--standalone` для 2.x
- [x] 3.3 `BlackbirdPathCustomizer`: `BLACKBIRD_OPENCODE_COMPATIBLE` и `BLACKBIRD_CONFIG_DIR` для версии по умолчанию, не перезаписывая уже заданные
- [x] 3.4 Юнит-тест построения команды и окружения для четырёх случаев: обычный, свой каталог, совместимость без каталога, совместимость с каталогом и 2.x

## 4. Настройки

- [x] 4.1 Флажок «opencode-compatible mode» и поле «opencode config directory». Поле доступно только при включённом флажке, поле каталога blackbird — только при выключенном
- [x] 4.2 Модели и просмотр файла переключаются на активный каталог, когда меняется режим, без Apply
- [x] 4.3 Группа «Import from opencode»: поле пути (файл или каталог) (не сохраняется) и кнопка; `--opencode-import` бинарником версии в каталоге проекта под модальным прогрессом; окно с выводом или с ошибкой; перечитывание файла
- [x] 4.4 isModified, apply и reset для новых полей

## 5. Проверка

- [x] 5.1 Сборка плагина и тесты (`./gradlew buildPlugin test`)
- [ ] 5.2 Установить плагин в SD-OpenIDE: панель, «+», выбор версии, Escape, пункт «⋮»
- [ ] 5.3 Вкладки во всех режимах D3: проверить командную строку и `blackbird debug config` внутри вкладки
- [ ] 5.4 Импорт: глобальный и по пути `opencode.json`, на копии `HOME`, чтобы не трогать настоящие файлы
- [ ] 5.5 Терминал: `blackbird --version` и переменные окружения для версии по умолчанию
