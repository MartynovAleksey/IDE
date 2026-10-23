## Why

Собираемая из исходников IDE должна называться **SD_IDE CE**, а не IntelliJ IDEA. Сейчас название IntelliJ видно в интерфейсе, в имени `.app` и в именах дистрибутивов. Кроме того, сборка использует тот же bundle id и тот же каталог настроек, что и официальная IntelliJ IDEA, поэтому на одном маке они конфликтуют.

## What Changes

- Пользовательское имя продукта `IntelliJ IDEA` → `SD_IDE CE`: заголовок окна, About, меню приложения, диалоги.
- Короткое имя продукта `IDEA` остаётся: из него строится папка проектов по умолчанию `~/IdeaProjects`.
- Путь артефактов в `.github/workflows/IntelliJ_IDEA.yml` → `out/sdide-ce/artifacts`.
- Имя бандла macOS `IntelliJ IDEA OSS.app` → `SD_IDE CE.app`; `.dmg` и архивы называются `sdide-ce-<build>`.
- **BREAKING**: собственный bundle id macOS и собственный каталог конфигурации и кэшей (`SDIDECE<версия>` вместо `IdeaIC<версия>`). Настройки, ранее созданные сборкой IntelliJ, автоматически не подхватываются.
- Логотип на заставке при запуске (`idea_community_logo.png` и `@2x`) и иконка приложения (macOS `.icns`, иконки в интерфейсе `idea-ce*.svg`, Windows `.ico`, Linux `product_128.png`) заменяются **одной и той же** картинкой: логотипом SD (`assets/sd-logo-source.png`) целиком, без обрезки, на прозрачном фоне.
- Имена для Windows и Linux (`fullName`, каталог установки, корневой каталог архива) меняются так же, чтобы бренд был везде одинаковым.
- Функциональность, состав плагинов, номер сборки (`IC-…`) и совместимость плагинов не меняются.

## Capabilities

### New Capabilities
- `product-branding`: имя продукта, видимое пользователю, имена артефактов и идентификаторы установки SD_IDE CE.

### Modified Capabilities
<!-- нет: в openspec/specs/ пока нет спецификаций -->

## Impact

- `community-resources/resources/idea/IdeaApplicationInfo.xml`: атрибуты `<names>`.
- `community-resources/resources/idea_community_logo.png` и `idea_community_logo@2x.png`: картинки заменяются, имена файлов остаются.
- `community-resources/resources/idea-ce.svg`, `idea-ce_16.svg`, `idea-ce_16@2x.svg`; `build/idea-community-images/mac/product.icns`, `win/product.ico`, `linux/product_128.png`: содержимое заменяется, имена файлов остаются.
- `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`: `getSystemSelector`, `getBaseArtifactName`, `getOutputDirectoryName`, а также customizer'ы для mac, Windows и Linux.
- Вывод сборки: `out/<новый каталог>/artifacts/*.dmg`.
- Не затрагивается: исходники платформы, плагины, Android, тесты платформы.
