## 1. Полноценный payload Hermes

- [x] 1.1 Разобрать `pm.activate()`/`pm.paths.store_root()` Hermes 0.22.5: какие пакеты обязательны и как указать Hermes на хранилище внутри payload
- [x] 1.2 Написать `plugins-external/hermes/payload/build_payload.py` (замена `make_portable_hermes.sh`): переносимый Hermes + инструменты через штатный `pm install --tools-only` + `manifest.json`; самопроверка `hermes --version` и `check_runtime() is None`
- [x] 1.3 Проверить скрипт локально на macOS arm64
- [x] 1.4 Workflow `.github/workflows/hermes-payload.yml`: нативная сборка на 4 ОС, выкладка в релиз `hermes-payload-<версия>`
- [x] 1.5 Закоммитить, запушить, запустить workflow; проверить, что все 4 сборки прошли самопроверку и архивы в релизе

## 2. Gradle-плагин

- [x] 2.1 Создать `plugins-external/hermes/` с Gradle wrapper и `build.gradle.kts` (IntelliJ Platform Gradle Plugin 2.19, `local(<SD-OpenIDE>)`, `bundledPlugin("org.jetbrains.plugins.terminal")`, since/until 262)
- [x] 2.2 Перенести в плагин панель, вкладки, настройки, `HermesCli`, PATH-customizer, иконку; `plugin.xml` с регистрациями
- [x] 2.3 Escape-dispatcher для IntelliJ IDEA (права и симлинки восстанавливает установщик плагинов)
- [x] 2.4 `payload/package_plugin.py` добавляет payload ОС в zip из `./gradlew buildPlugin` → `hermes-intellij-0.1.0-<ОС>.zip`

## 3. SD-OpenIDE без Hermes

- [x] 3.1 Удалить Hermes из `CommunityRepositoryModules.kt`, `terminal.xml`, `plugins/terminal/.../opencode/Hermes*`, иконки и `build/sd-hermes/`; оставить `AgentSessionTabs.kt`, раскладку и Escape для `Hermes`
- [x] 3.2 Собрать SD-OpenIDE macOS arm64: Hermes в дистрибутиве нет, OpenCode работает

## 4. Проверка

- [x] 4.1 Установить zip плагина macOS arm64 в SD-OpenIDE: панель Hermes, вкладка запускает `hermes`, страница настроек, нет `install out of sync`
- [x] 4.2 Установить тот же zip в IntelliJ IDEA 2026.2 и проверить панель и Escape (Escape следует настройке Terminal «Move focus to the editor with»)
- [x] 4.3 С подтверждения пользователя опубликовать zip плагина в GitHub-релиз (выложены в релиз SD-OpenIDE `0.0.3`)
