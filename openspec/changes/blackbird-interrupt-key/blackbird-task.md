# blackbird: параметр `interrupt_hint` — текст клавиши в подсказке о прерывании

**Репозиторий:** https://github.com/MartynovAleksey/blackbird (blackbird — переименованный форк opencode).
**Ветки:**
- `blackbird/v1`: основа opencode 1.19.5, релизы v1.19.5.x;
- `blackbird/v2`: основа opencode 2.2.5, релизы v2.2.5.x.

Изменение нужно в обеих ветках.

## Проблема

Пока агент работает, строка состояния промпта показывает «esc interrupt», а после первого нажатия — «esc again to interrupt». Слово `esc` зашито в код.

В плагине Blackbird для IntelliJ простой Escape до blackbird не доходит, а Ctrl+Esc доходит. Терминал IDE (JediTerm) не поддерживает протокол kitty и на Ctrl+Esc отправляет обычный ESC, поэтому blackbird прерывает агента по своей обычной привязке `escape`. Поэтому в IDE подсказка должна говорить «ctrl+esc», а клавиши должны работать как раньше.

## Что сделать

1. Добавить в схему конфигурации интерфейса необязательный строковый параметр `interrupt_hint` с описанием «Key text shown in the interrupt hint».
   - v1: схема TUI-конфигурации в `packages/tui/src/config/index.tsx`, файл `tui.json`.
   - v2: схема конфигурации CLI/TUI, файл `cli.json`. Схема CLI собрана из `packages/tui/src/config/index.tsx` (см. `packages/cli/src/config/schema.ts`).
2. В подсказке о прерывании `packages/tui/src/component/prompt/index.tsx` вместо литерала `esc{" "}` выводить `interrupt_hint`. Если он не задан или пуст, выводить `esc`.
   - v1: около строки 1587.
   - v2: компонент подсказки около строки 140.
   - v2: если в mini-режиме (`packages/tui/src/mini/footer.view.tsx`) есть та же подсказка, применить там тот же параметр.
3. **Не менять** привязки клавиш (`keybinds`, `session.interrupt`) и логику прерывания: двойное нажатие и сброс через 5 с. Параметр влияет только на текст.
4. Параметр должен приниматься и из внешних источников, которые использует плагин IDE:
   - v1: файл из `BLACKBIRD_TUI_CONFIG`, например `{"interrupt_hint":"ctrl+esc"}`;
   - v2: `BLACKBIRD_CLI_CONFIG_CONTENT={"interrupt_hint":"ctrl+esc"}`.

## Критерии приёмки

- Без `interrupt_hint` всё как раньше: «esc interrupt» / «esc again to interrupt», Esc прерывает.
- С `"interrupt_hint": "ctrl+esc"` в `tui.json` (v1) или `cli.json` (v2):
  - подсказка — «ctrl+esc interrupt» / «ctrl+esc again to interrupt»;
  - прерывание срабатывает на те же клавиши, что и без параметра.
- Через `BLACKBIRD_TUI_CONFIG` (v1) и `BLACKBIRD_CLI_CONFIG_CONTENT` (v2) параметр работает так же. В режиме `--opencode-compatible` тоже.
- Тесты:
  - подсказка по умолчанию и с заданным `interrupt_hint`, в каждой ветке;
  - существующие тесты проходят;
  - конфигурация с `interrupt_hint` проходит валидацию схемы.
- Выпустить релизы `v1.19.5.6` и `v2.2.5.6` в формате предыдущих:
  - архивы `blackbird-v<версия>-{darwin,linux}-{arm64,x64}.tar.gz` и `blackbird-v<версия>-windows-{arm64,x64}.zip`;
  - файл `SHA256SUMS`;
  - в описании — раздел «Что нового по сравнению с <предыдущей версией>».
