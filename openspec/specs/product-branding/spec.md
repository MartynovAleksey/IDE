# product-branding Specification

## Purpose
Брендинг сборки SD-OpenIDE: видимое имя, заставка, иконки, имена артефактов и идентификаторы, отделяющие её от IntelliJ IDEA. Картинки генерирует `build/sd-branding/make_images.py`.
## Requirements
### Requirement: Имя продукта в интерфейсе
Собранная IDE SHALL показывать полное имя продукта `SD-OpenIDE` везде, где платформа берёт его из `ApplicationNamesInfo`: заголовок окна, диалог About, меню приложения macOS, диалоги перезапуска и закрытия.

#### Scenario: Диалог About
- **WHEN** пользователь открывает Help → About (macOS: меню приложения → About)
- **THEN** в диалоге показано имя `SD-OpenIDE`, а `IntelliJ IDEA` нигде не встречается

#### Scenario: Меню приложения macOS
- **WHEN** IDE запущена на macOS
- **THEN** меню приложения в строке меню называется `SD-OpenIDE`

### Requirement: Логотип на заставке
Заставка при запуске SHALL показывать логотип SD из `build/sd-branding/sd-openide-source.png` целиком, без искажения пропорций и без белого фона, в обычном (640×400) и Retina (1280×800) разрешении.

#### Scenario: Запуск IDE
- **WHEN** пользователь запускает SD-OpenIDE
- **THEN** на заставке виден логотип SD целиком, а логотипа IntelliJ IDEA нет

#### Scenario: Retina-экран
- **WHEN** IDE запускается на экране с масштабом 2x
- **THEN** заставка использует `idea_community_logo@2x.png` размером 1280×800 и выглядит чётко

### Requirement: Иконка приложения
Иконка приложения SHALL быть тем же логотипом SD, что и на заставке, целиком, без обрезки, на прозрачном фоне, во всех местах, где сборка берёт иконку продукта: macOS `.icns`, `idea-ce*.svg` в интерфейсе, Windows `product.ico` и Linux `product_128.png`.

#### Scenario: Dock и Finder
- **WHEN** `SD-OpenIDE.app` показывается в Finder или запущена и видна в Dock
- **THEN** иконка — логотип SD целиком, а не логотип IntelliJ IDEA

#### Scenario: Тёмный фон
- **WHEN** иконка показывается на тёмном фоне (тёмная тема, Dock)
- **THEN** вокруг логотипа нет белого прямоугольника и белой каймы

### Requirement: Имя бандла и артефактов macOS
Сборка для macOS SHALL выпускать бандл `SD-OpenIDE.app`, а его `.dmg` и архивы SHALL называться с префиксом `sd-openide-`.

#### Scenario: Артефакт сборки для macOS aarch64
- **WHEN** запускается `./installers.cmd -Dintellij.build.target.os=mac -Dintellij.build.target.arch=aarch64` на macOS
- **THEN** в каталоге артефактов есть `sd-openide-<build>-aarch64.dmg`, а внутри него лежит `SD-OpenIDE.app`

### Requirement: Изоляция от IntelliJ IDEA
SD-OpenIDE SHALL использовать собственный bundle id macOS и собственный каталог конфигурации, системных файлов, плагинов и логов, не совпадающие с официальными сборками IntelliJ IDEA.

#### Scenario: Параллельная установка с IntelliJ IDEA
- **WHEN** на одном маке установлены IntelliJ IDEA и SD-OpenIDE
- **THEN** у них разные `CFBundleIdentifier`, а SD-OpenIDE хранит настройки в `~/Library/Application Support/JetBrains/SDOpenIDE<год>.<минор>` и не трогает каталоги `IdeaIC*`

### Requirement: Функциональность не меняется
Переименование MUST NOT менять состав бандла, плагины, номер сборки с префиксом `IC-` и совместимость с плагинами из Marketplace.

#### Scenario: Список плагинов
- **WHEN** сравниваются каталоги `plugins/` в сборках до и после изменения
- **THEN** набор плагинов совпадает

#### Scenario: Номер сборки
- **WHEN** пользователь открывает диалог About
- **THEN** номер сборки начинается с `IC-`

