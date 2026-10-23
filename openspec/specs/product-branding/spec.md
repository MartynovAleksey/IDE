# product-branding Specification

## Purpose
TBD - created by archiving change rebrand-to-sd-ide-ce. Update Purpose after archive.
## Requirements
### Requirement: Имя продукта в интерфейсе
Собранная IDE SHALL показывать полное имя продукта `SD_IDE CE` везде, где платформа берёт его из `ApplicationNamesInfo`: заголовок окна, диалог About, меню приложения macOS, диалоги перезапуска и закрытия.

#### Scenario: Диалог About
- **WHEN** пользователь открывает Help → About (macOS: меню приложения → About)
- **THEN** в диалоге показано имя `SD_IDE CE`, а `IntelliJ IDEA` нигде не встречается

#### Scenario: Меню приложения macOS
- **WHEN** IDE запущена на macOS
- **THEN** меню приложения в строке меню называется `SD_IDE CE`

### Requirement: Логотип на заставке
Заставка при запуске SHALL показывать логотип SD из `assets/sd-logo-source.png` целиком, без искажения пропорций и без белого фона, в обычном (640×400) и Retina (1280×800) разрешении.

#### Scenario: Запуск IDE
- **WHEN** пользователь запускает SD_IDE CE
- **THEN** на заставке виден логотип SD целиком, а логотипа IntelliJ IDEA нет

#### Scenario: Retina-экран
- **WHEN** IDE запускается на экране с масштабом 2x
- **THEN** заставка использует `idea_community_logo@2x.png` размером 1280×800 и выглядит чётко

### Requirement: Иконка приложения
Иконка приложения SHALL быть тем же логотипом SD, что и на заставке, целиком, без обрезки, на прозрачном фоне, во всех местах, где сборка берёт иконку продукта: macOS `.icns`, `idea-ce*.svg` в интерфейсе, Windows `product.ico` и Linux `product_128.png`.

#### Scenario: Dock и Finder
- **WHEN** `SD_IDE CE.app` показывается в Finder или запущена и видна в Dock
- **THEN** иконка — логотип SD целиком, а не логотип IntelliJ IDEA

#### Scenario: Тёмный фон
- **WHEN** иконка показывается на тёмном фоне (тёмная тема, Dock)
- **THEN** вокруг логотипа нет белого прямоугольника и белой каймы

### Requirement: Имя бандла и артефактов macOS
Сборка для macOS SHALL выпускать бандл `SD_IDE CE.app`, а его `.dmg` и архивы SHALL называться с префиксом `sdide-ce-`.

#### Scenario: Артефакт сборки для macOS aarch64
- **WHEN** запускается `./installers.cmd -Dintellij.build.target.os=mac -Dintellij.build.target.arch=aarch64` на macOS
- **THEN** в каталоге артефактов есть `sdide-ce-<build>-aarch64.dmg`, а внутри него лежит `SD_IDE CE.app`

### Requirement: Изоляция от IntelliJ IDEA
SD_IDE CE SHALL использовать собственный bundle id macOS и собственный каталог конфигурации, системных файлов, плагинов и логов, не совпадающие с официальными сборками IntelliJ IDEA.

#### Scenario: Параллельная установка с IntelliJ IDEA
- **WHEN** на одном маке установлены IntelliJ IDEA и SD_IDE CE
- **THEN** у них разные `CFBundleIdentifier`, а SD_IDE CE хранит настройки в `~/Library/Application Support/JetBrains/SDIDECE<год>.<минор>` и не трогает каталоги `IdeaIC*`

### Requirement: Функциональность не меняется
Переименование MUST NOT менять состав бандла, плагины, номер сборки с префиксом `IC-` и совместимость с плагинами из Marketplace.

#### Scenario: Список плагинов
- **WHEN** сравниваются каталоги `plugins/` в сборках до и после изменения
- **THEN** набор плагинов совпадает

#### Scenario: Номер сборки
- **WHEN** пользователь открывает диалог About
- **THEN** номер сборки начинается с `IC-`

