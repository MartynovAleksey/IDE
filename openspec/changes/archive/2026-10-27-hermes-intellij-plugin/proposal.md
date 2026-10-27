## Why

Hermes Agent нужен в IDE полноценным — с инструментами, которые ставит его официальный установщик (`ripgrep`, `node`/`npm`, `ffmpeg`), — и отдельным модулем, а не частью дистрибутива SD-OpenIDE: так его можно ставить и обновлять независимо от IDE и использовать в обычной IntelliJ IDEA 2026.2. Сейчас (незакоммиченная реализация) Hermes вшит в SD-OpenIDE и без инструментов, из-за чего Hermes сообщает `install out of sync`.

## What Changes

- Новый плагин **Hermes for IntelliJ** — отдельный Gradle-проект на IntelliJ Platform Gradle Plugin в `plugins-external/hermes/` этого репозитория. Совместим с IntelliJ IDEA и SD-OpenIDE 2026.2 (сборка 262), зависит от встроенного terminal-плагина.
- Плагин даёт панель **Hermes** (вкладки-консоли с «+», консоль после выхода), страницу **Settings | Tools | Hermes** (модель, провайдер, Base URL, интерфейс, кнопки мастеров и файлов, флаг авто-setup — по умолчанию выключен), пункт «Hermes Settings…» в меню «⋮», `hermes` в `PATH` терминалов, Escape в процесс Hermes.
- **Полноценный Hermes внутри zip плагина**, отдельный zip на каждую ОС: `hermes-intellij-<версия>-{macos-arm64,linux-x64,linux-arm64,windows-x64}.zip`. Внутри — переносимый Hermes v0.22.5 (CPython 3.14.6 + зависимости + исходники) и инструменты из `pm/lock.json` Hermes (минимум `ripgrep`, `node`, `npm`, `ffmpeg`), разложенные так, что Hermes считает установку полной.
- **BREAKING (для незакоммиченной ветки)**: встроенный Hermes убирается из SD-OpenIDE — панель, настройки, скачивание архивов и флаг `sd.openide.bundle.hermes`. В SD-OpenIDE остаются OpenCode, общая логика вкладок, место кнопки `Hermes` в раскладке и Escape для id `Hermes` (чтобы плагин в SD-OpenIDE работал как родной).

## Capabilities

### New Capabilities
- `hermes-plugin`: плагин Hermes для IntelliJ — панель, настройки, полноценный Hermes в zip под каждую ОС, сборка.

### Modified Capabilities
<!-- нет: embedded-opencode и product-branding не меняются -->

## Impact

- Новый каталог `plugins-external/hermes/` (Gradle wrapper, `build.gradle.kts`, исходники плагина, скрипт сборки payload).
- SD-OpenIDE: удаление Hermes из `CommunityRepositoryModules.kt`, `terminal.xml`, `plugins/terminal/.../opencode/Hermes*`; `build/sd-hermes/` переезжает в плагин.
- Размер zip плагина: примерно 250–450 МБ на ОС (payload Hermes + инструменты).
- Сборка плагина скачивает IntelliJ IDEA 2026.2.3 (для компиляции) и инструменты Hermes; публикация zip — в GitHub-релиз по подтверждению.
