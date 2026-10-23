## 1. Имя продукта

- [x] 1.1 В `community-resources/resources/idea/IdeaApplicationInfo.xml` заменить `<names product="IDEA" fullname="IntelliJ IDEA" …>` на `fullname="SD_IDE CE"`; `product="IDEA"`, `script` и `motto` оставить

- [x] 1.2 Сгенерировать `assets/make_images.py`: прозрачный мастер, `generated/idea_community_logo.png` 640×400 и `@2x` 1280×800, `sd-icon-1024.png`
- [x] 1.3 Проверить размеры и альфу (`sips -g … -g hasAlpha`) и посмотреть глазами на тёмном фоне (белой каймы нет)
- [x] 1.4 Скопировать `generated/idea_community_logo*.png` в `community-resources/resources/` (после окончания эталонной сборки)

## 1a. Иконка приложения

- [x] 1a.1 Иконка-мастер `assets/sd-icon-1024.png`: весь логотип на прозрачном фоне, утверждён пользователем
- [x] 1a.2 Собрать `build/idea-community-images/mac/product.icns`: `make_images.py` в `.iconset` (16…512 и `@2x`), затем `iconutil -c icns`
- [x] 1a.3 Сделать `build/idea-community-images/win/product.ico` и `linux/product_128.png` (128×128) через `make_images.py` (Pillow)
- [x] 1a.4 Заменить `community-resources/resources/idea-ce.svg` (64), `idea-ce_16.svg` (16), `idea-ce_16@2x.svg` (32) на SVG с PNG внутри (data URI) того же `width/height/viewBox`
- [x] 1a.5 Проверить: `iconutil -c iconset product.icns` раскрывается, `.ico` и `.png` открываются в Preview

## 2. Идентификаторы и артефакты сборки

- [x] 2.1 В `IdeaCommunityProperties.getSystemSelector` вернуть `SDIDECE<major>.<minor>`
- [x] 2.2 `getBaseArtifactName` → `sdide-ce-<build>`, `getOutputDirectoryName` → `sdide-ce`
- [x] 2.3 `ideaCommunityMacCustomizer`: `bundleIdentifier` → `com.sdide.ce`, `rootDirectoryName` → `SD_IDE CE.app`
- [x] 2.4 `ideaCommunityWindowsCustomizer`: `fullName` → `SD_IDE CE`, `installDirNameHandler` → `SD_IDE CE`
- [x] 2.5 `ideaCommunityLinuxCustomizer`: `rootDirectoryName` → `sdide-ce`
- [x] 2.6 Убедиться, что `AndroidStudioProperties` (наследник) не затронут по смыслу: он переопределяет только `platformPrefix` и плагины
- [x] 2.7 `.github/workflows/IntelliJ_IDEA.yml`: `artifacts_dir` → `out/sdide-ce/artifacts`

## 3. Сборка и проверка

- [x] 3.1 Собрать `./installers.cmd -Dintellij.build.target.os=mac -Dintellij.build.target.arch=aarch64`; если упадёт проверка, ожидающая старое имя, исправить её
- [x] 3.2 Проверить, что в `out/sdide-ce/artifacts/` есть `sdide-ce-*-aarch64.dmg`, а внутри лежит `SD_IDE CE.app`
- [x] 3.3 В `Info.plist` бандла: `CFBundleName` равно `SD_IDE CE`, `CFBundleIdentifier` равен новому id
- [ ] 3.4 Запустить `.app`: заголовок окна, About и меню приложения показывают `SD_IDE CE`, номер сборки начинается с `IC-`
- [ ] 3.4a На заставке виден логотип SD целиком, прогресс-бар не закрывает надпись
- [ ] 3.4b В Finder и Dock у `SD_IDE CE.app` иконка слона; в About и на экране приветствия SVG-иконка отображается (если нет — векторизовать, см. design)
- [x] 3.5 Настройки создаются в `~/Library/Application Support/JetBrains/SDIDECE2026.2`
- [x] 3.6 Список `Contents/plugins/` совпадает со сборкой до изменения
- [ ] 3.7 Выполнить `graphify update .`
