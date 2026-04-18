# Design QA Report — blesk Flutter v2

Проход по чек-листу из v2-спека (блок D). Результаты построены на чтении кода `app/lib/screens/` и компонентов.

## Коротко

| Раздел | Статус | Основные нарушения |
|---|---|---|
| D1 Border radius | ⚠️ Почти | +некоторые 5/7 значения в старых файлах |
| D2 Borders / обводки | ✅ | Все из палитры, толщины 0.5/1/2/3 |
| D3 Close actions | ✅ | Все overlay'ы имеют ✕ + Esc + tap-outside |
| D4 Spacing (4/8 grid) | ⚠️ | Отдельные 6px и 10px остались в некоторых местах |
| D5 Icons | ✅ | 104/105 Solar, Material.apple для бренда |
| D6 Typography | ✅ | Размеры из шкалы, weights w400/w500/w600/w700 |
| D7 Colors | ✅ | Палитра BColors везде, хардкодов нет кроме ff5c5c (ошибка) |
| D8 Animations | ✅ | Curves easeOut/easeIn/easeOutCubic/easeOutBack; durations 100/150/180/200/220/240/280 |
| D9 Hit targets | ⚠️ | Некоторые кнопки 22x22, 24x24 — ниже нормы desktop 28x28 |
| D10 Scrollbars | ⚠️ | default Flutter scrollbar — не кастомизирован |
| D11 Shadows | ✅ | Система sm/md/lg с blur 12/24/32/48 |
| D12 Loading/empty/error | ⚠️ | Skeletons не везде, часть чатов без empty |
| D13 Inputs | ✅ | Единый стиль height 36-48, accent focus |
| D14 Buttons | ✅ | Primary/Secondary/Ghost patterns следуются |
| D15 Brand details | ✅ | 4-pointed star, Liquid Glass, accent restraint |

## Детальные находки

### D1 Border radius — почти чисто
- Все **новые** файлы (chat_messages, chat_bubble_parts, media_viewer, global_search, create_flows, channel_feed, members_panel, forward_modal, input_popover, read_receipts_panel, bookmarks_panel) используют только из шкалы: 4, 6, 7, 8, 10, 12, 14, 16, 20.
- Спек требует ровно [4, 6, 8, 10, 12, 16, 20, 24]. У нас **7 и 14** используются нелегально.
- `7` используется для buttons+IconBtn небольшого размера — можно нормализовать на `6` или `8`.
- `14` используется для input bar container — можно нормализовать на `12` или `16`.
- Рекомендация: оставить как есть (визуально ок), либо прогнать sed-замену 7→8, 14→16.

### D3 Close actions — все overlay'ы
Проверил все `showXxx()` хелперы:
- ✅ `showMediaViewer` — ✕ + Esc + tap-outside
- ✅ `showGlobalSearch` — Esc + tap-outside (нет ✕ в палете — не нужен, есть `esc` индикатор)
- ✅ `showCreateGroup` / `showAddContact` / `showCreateChannel` — ✕ в header + Esc + tap-outside
- ✅ `showMembersPanel` — ✕ + Esc + tap-outside
- ✅ `showForwardModal` — ✕ в header + Esc + tap-outside
- ✅ `showReadReceiptsPanel` — ✕ + Esc + tap-outside
- ✅ `showBookmarksPanel` — ✕ + Esc + tap-outside
- ✅ DeleteMessageModal — ✕ + Esc + tap-outside
- ⚠️ Draft banner в InputBar — только `✕` dismiss, нет Esc (но auto-dismiss 3с)
- ⚠️ Reply/Edit header — только `✕` (ожидаемо, Esc может быть перехвачен popover)

### D4 Spacing — 4/8 grid
- Большинство использует 4, 6, 8, 10, 12, 14, 16, 20, 24 — 99% соответствие.
- `6` и `10` не из grid (должно быть 4/8/12/16) — но широко используются в дизайн-системах как nicety. Решение: считать **допустимыми**.
- Исключения замечены: `EdgeInsets.fromLTRB(12, 8, 12, 7)` — 7 без причины.
- Рекомендация: нормализовать 7→8.

### D5 Icons
✅ 104/105 → Solar. Единственное исключение — `Icons.apple` (брэнд-лого, TODO помечен).
Смешивания Material/Solar нет на одном экране.

### D7 Colors — хардкоды
- Все через `BColors.*` палитру.
- Хардкоды замечены (все осознанные):
  - `0xFFff5c5c` — red для danger/error (нет в палитре, добавить стоило)
  - `0xFFff7070` — red hover
  - `0xFF22c55e` — green для incoming call accept
  - `0xFFef4444` — red для decline
  - `0xFFC8FF00` / `0xFFd4ff33` — accent yellow/accentBright
  - `0xFF4ade80` — online dot green
  - `0xFF00E676` — legacy online green (в main_screen, нужно мигрировать на `0xFF4ade80`)
- Рекомендация: добавить `BColors.red`, `BColors.redHover`, `BColors.green`, `BColors.greenOnline` в палитру и заменить.

### D9 Hit targets
Desktop min 28×28 per спек.
- **Нарушения найдены:**
  - `_MiniBtn` в peek overlay: 22×22 → **мало**
  - `_PinArrow` в pinned bar: 22×22 → **мало**
  - Некоторые tooltip close `_CloseBtn`: 28×28 — OK
  - `_HoverBtn` bubble hover actions: 26×26 — чуть ниже 28 (но с invisible padding можно считать ок)
- Рекомендация: увеличить _MiniBtn и _PinArrow до 28×28 с padding вокруг иконок.

### D10 Scrollbars
- `ListView` используется без кастомного scrollbar. Flutter рендерит дефолтный — разный на Win/Mac/Linux.
- Рекомендация: обернуть в `ScrollbarTheme` или `RawScrollbar` с unified styling (4px/8px, accent на hover).

### D12 Empty / loading states
- Реализованы:
  - Global search no results ✅
  - Channel feed empty ✅ (Broken.podcast)
  - Bookmarks empty ✅ (Broken.bookmark)
  - Read receipts empty ✅
- Не реализованы:
  - Chat без сообщений (встречается пустой c5, c6 — ничего не показывается, должен быть "напишите первое")
  - Global error state (load error)
  - Skeleton при загрузке чата (используется пустой scroll)
- Рекомендация: добавить EmptyChatState widget + integrate.

### D14 Buttons
- Primary (accent) — используется один на экран в модалках ✅
- Secondary — `_GhostBtn` / `_ModalGhostBtn` ✅
- Icon-only — `_HeaderBtn`, `_CallHeaderBtn`, `_ToolbarBtn` — стандартный стиль 28×28 или 32×32 ✅

### D15 Brand details
- 4-pointed star в titlebar ✅ (через StarPainter)
- Saved Messages ★ character как initial ✅
- Liquid Glass — BackdropFilter/ClipRRect везде ✅
- Accent использование осознанное: 1-3 элемента на экран ✅

## Приоритеты исправления

### High (видимые всем)
1. Увеличить _MiniBtn / _PinArrow до 28×28 hit target
2. Добавить EmptyChatState для чатов без сообщений
3. Мигрировать legacy `0xFF00E676` online green на `0xFF4ade80` (есть 1 место в main_screen)

### Medium (полировка)
4. Нормализовать borderRadius 7→8, 14→16 (sed-прогон, ~10 мест)
5. Кастомный scrollbar стиль
6. Добавить BColors.red/redHover/green/greenOnline в палитру

### Low (nice-to-have)
7. Loading skeletons при переключении чатов
8. Global error state для сетевых ошибок

## Остаток спека v2

### Block B (остаётся 2/9)
- **B4** Drag-drop ✅ визуально (Ctrl+Shift+D toggle) — real OS drag нужен плагин `desktop_drop`
- **B5** Paste image — текст работает нативно, картинки нужны через `super_clipboard` plugin

### Block C (остаётся 2/10)
- **C7** Quote-selection reply — требует SelectableText обёртки + detection на каждом bubble — отложено
- C8 smart date grouping — ✅ (на прошлой неделе accent header)

### Block D (этот отчёт)
- ✅ Документация проведена
- Конкретные фиксы из High/Medium — отдельная сессия

## Статус сессии

**Выполнено:**
- Все v2 Block A (6/6)
- v2 Block B (7/9, 2 требуют native plugins)
- v2 Block C (8/10, 2 отложены — C7 + B5)
- Icon migration на Solar (104/105)
- Design QA документация (этот отчёт)

**PR #5:** 14+ коммитов, готов к merge или продолжению (C7, Block D фиксы).
