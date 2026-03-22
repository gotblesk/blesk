# blesk — Orbit + Metaball Redesign Spec

**Дата:** 2026-03-20
**Статус:** Утверждён

## Обзор

Кардинальный редизайн UI blesk с Liquid Glass на Orbit + Ferrofluid Metaball. SVG filter `feGaussianBlur + feColorMatrix` (goo effect) применяется к кружкам/кнопкам. Текст всегда ВНЕ filter. Каждый интерактивный элемент использует 3-слойную архитектуру: goo-слой (в filter) → visual-слой (фото/текст вне filter) → hit-слой (невидимая зона для hover/click).

## Mockups (утверждены)

- `docs/mockups/ORBIT-AVATARS-v3.html` — Orbit с отлипанием
- `docs/mockups/ORBIT-MINI-PROFILE-v2.html` — Мини-профиль при клике
- `docs/mockups/CHATLIST-BLOB-CARDS-v3.html` — Чат-лист blob-карточки
- `docs/mockups/FULL-MAIN-METABALL-v2.html` — Полный main screen

## Архитектура: 3-слойная система

Везде где используется SVG metaball filter, структура одинаковая:

```
Слой 1 (GOO): filter: url(#goo) — цветные кружки/формы, blob-мержатся
Слой 2 (VISUAL): pointer-events: none — фото, текст, иконки (чёткие)
Слой 3 (HIT): z-index выше всех — невидимые зоны для hover/click (НЕ двигаются)
```

Правило: SVG filter ТОЛЬКО на элементы БЕЗ текста. Текст, иконки SVG, фото — всегда ВНЕ filter.

## Компоненты

### 1. Orbit (избранные друзья)

**Layout:** Полный круг, плотный. Центр = аватар пользователя.
- R рассчитывается от высоты контейнера: `R = containerHeight * 0.21` (sidebar 200px → R≈42, standalone → R≈52)
- **Максимум 8 друзей** в orbit. Пользователь выбирает "избранных" (pin/star). Остальные только в чат-листе.
- При 0 друзей: пустая orbit с placeholder "Добавь друзей" blob-кнопкой
- При 1-3 друга: увеличить размер аватаров (fSize=44) и R уменьшить

**Двойной слой аватаров:**
- GOO: цветной кружок (hue пользователя) + tentacle-мостик к центру
- VISUAL: фото-аватарка с цветным ободком (border: 2.5px solid hue) + glow (box-shadow)
- HIT: невидимый круг (avatar size + 30px), фиксированный, не двигается

**Hover-отлипание:**
- HIT mouseenter → аватар отъезжает от центра (maxPull=30-38px)
- Мостик тянется и истончается (width растёт, height уменьшается)
- Glow усиливается (14px → 30px)
- HIT mouseleave → пружинит обратно (interpolation speed 0.18)

**Клик → мини-профиль:**
- Popup 260px шириной
- Координаты: `getBoundingClientRect()` аватара, offset наружу от центра orbit
- Приоритет размещения: 1) справа в chat panel, 2) слева, 3) снизу/сверху
- Минимальный margin от краёв окна: 8px
- Аватар с glow-ободком торчит сверху карточки (top: -30px)
- Кнопки: Написать (c8ff00) + Позвонить (green) в одном ряду, Профиль отдельно
- transform-origin от точки аватара, scale анимация появления
- Overlay для закрытия при клике вне

**Фото-аватарки:**
- Если пользователь загрузил фото: фото внутри цветного ободка, glow
- Если нет фото: буква на тёмном фоне внутри цветного ободка

**SVG filter (orbit):**
```
stdDeviation="12", matrix values="...22 -9"
```

### 2. Чат-лист (blob-карточки)

**CSS glow + scale эффекты** на карточках. SVG filter применяется ТОЛЬКО к обёртке аватар+badge (badge blob-прилипает к аватару).

**Каждая карточка:**
- background: `hsla(hue, 60%, 50%, 0.035)` — полупрозрачный цвет hue
- border-radius: 16px
- Аватар с цветным ободком (border: 2.5px solid hue) + glow (box-shadow 10px)

**Hover:**
- transform: scale(1.02) translateX(2px)
- background alpha увеличивается (0.035 → 0.06)
- box-shadow: glow усиливается (0 0 24px hue + inset glow)
- Аватар scale(1.08)

**Active (выбранный чат):**
- background alpha: 0.07
- border: 1px solid rgba(200,255,0,0.05)

**Typing в превью:**
- 3 точки (5px, #c8ff00) с анимацией bounce, inline рядом с текстом "печатает"

**Badge:**
- #c8ff00, border: 2px solid #08060f, на аватаре сверху-справа

### 3. Titlebar (blob-кнопки)

**SVG filter** на 3 кнопки (min/max/close):
- 14px кружки, gap: 1px
- Цвета: жёлтый (min), зелёный (max), красный (close) — полупрозрачные (opacity 0.6)
- Hover: scale(1.5), full opacity, box-shadow glow, иконка появляется (–, □, ×)

**Filter:** `stdDeviation="3", matrix "...14 -5"`

### 4. Dock (вертикальная навигация)

**SVG filter** на группу иконок:
- 38px кнопки, border-radius: 12px
- Active: bg rgba(200,255,0,0.08), stroke #c8ff00
- Hover: bg rgba(200,255,0,0.1), scale(1.1)

**Badge уведомлений:** ВНЕ filter (z-index: 5, border: 2px solid bg)

**Filter:** `stdDeviation="5", matrix "...16 -6"`

### 5. Typing indicator (в чате)

**SVG filter** на 3 точки:
- 10px кружки, rgba(200,255,0,0.4)
- Анимация bounce: translateY(-10px), 1.4s, staggered 0.15s
- Текст "Vohog печатает..." — СНАРУЖИ filter

**Filter:** `stdDeviation="5", matrix "...20 -8"`

### 6. Voice Pill

**SVG filter** на мини-аватары:
- 22px кружки, margin-left: -4px (перекрытие)
- Текст "gemoglobin" и dot — СНАРУЖИ filter
- Позиция: bottom: 16px, но при наличии input — поднять выше (bottom: 60px)

**Filter:** `stdDeviation="3", matrix "...14 -5"`

### 7. Call кнопки (в header чата)

**SVG filter** на 2 кнопки (телефон + видео):
- 32px, border-radius: 10px, gap: 3px
- Hover: scale(1.1), bg усиливается

**Filter:** `stdDeviation="4", matrix "...16 -6"`

### 8. Send кнопка

**SVG filter:**
- 36px круг, #c8ff00
- Hover: scale(1.15)
- При отправке: ripple blob (scale 1→3, opacity 0.5→0)

**Filter:** `stdDeviation="3", matrix "...14 -5"`

### 9. Сообщения (пузыри)

**НЕ используют SVG filter** (текст внутри).

**Группировка:**
- Consecutive from same person → radius меняется (first: full rounded, subsequent: small radius на стороне стыка)
- Own: зеленоватый bg rgba(200,255,0,0.08)
- Incoming: белый bg rgba(255,255,255,0.025)
- Анимация появления: scale(0.85)→1 + translateY(6px)→0

## Цвета

- Фон: #08060f
- Акцент: #c8ff00
- Online: #4ade80
- Danger: #ef4444
- Текст: rgba(255,255,255,0.85)
- Muted: rgba(255,255,255,0.25)
- Hue пользователя: 0-360, используется для ободков, glow, фона карточек

## SVG Filter — каноничный шаблон

Все filter используют ОДИНАКОВУЮ структуру с явными `result` именами:

```xml
<filter id="goo-{name}">
  <feGaussianBlur in="SourceGraphic" stdDeviation="N" result="blur"/>
  <feColorMatrix in="blur" mode="matrix"
    values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 A B" result="goo"/>
  <feComposite in="SourceGraphic" in2="goo" operator="atop"/>
</filter>
```

**ВАЖНО:** `in2="goo"` ссылается на `result="goo"` из feColorMatrix. НЕ `in2="g"`.

| Элемент | stdDeviation | A / B | Сила |
|---------|-------------|-------|------|
| Titlebar | 3 | 14 / -5 | Мягкий |
| Dock | 5 | 16 / -6 | Средний (статичный, без hover-separation) |
| Orbit | 12 | 22 / -9 | Сильный |
| Call btns | 4 | 16 / -6 | Средний |
| Typing | 5 | 20 / -8 | Сильный |
| Send | 3 | 14 / -5 | Мягкий |
| Voice pill | 3 | 14 / -5 | Мягкий |
| Chat avatar+badge | 3 | 14 / -5 | Мягкий |

## Что удалить (старый UI)

- NavShelf.jsx + NavShelf.css (горизонтальные табы)
- Плавающие чат-окна (useWindowManager.js)
- OrbitPanel.jsx + VibeMeter.jsx
- Таб "Голос" / "Друзья" / "Настройки" как отдельные экраны
- Текущий MainScreen.jsx (переписать)
- Текущий TitleBar.jsx (переписать)

## Что создать (новые компоненты)

- MetaballFilter.jsx — переиспользуемый SVG filter
- OrbitMainScreen.jsx — новый главный экран (dock + sidebar + panel)
- OrbitCircle.jsx — круговая orbit друзей с 3-слойной архитектурой
- MiniProfile.jsx — popup при клике на друга
- ChatListCard.jsx — blob-карточка чата с CSS glow
- MetaballTitleBar.jsx — titlebar с blob-кнопками
- VerticalDock.jsx — левый dock с metaball иконками
- MetaballTyping.jsx — blob typing dots
- VoicePill.jsx — минимизированная голосовая
- VoiceOverlay.jsx — fullscreen overlay голосовой

## Технические решения

### hitArea для hover (предотвращение тряски)
Невидимый div больше элемента на 30px, фиксированный, не двигается при hover. Все mouse events на hitArea, визуальный элемент двигается через JS.

### Двойной слой для фото в metaball
GOO-слой: цветной кружок (hue) — даёт blob-мостики. VISUAL-слой: фото поверх с clip-path circle — чёткое. Оба двигаются синхронно через JS.

### Voice pill позиция
Pill позиционируется НЕ через фиксированный bottom, а через CSS relative к input-row:
- Pill внутри flex-layout panel, перед input
- Или: CSS custom property `--input-area-height` обновляется через ResizeObserver
- bottom: `calc(var(--input-area-height) + 16px)`

### Dock — статичный metaball
Dock кнопки в одном filter, но hover НЕ отделяет кнопку от группы (нет 3-слойной архитектуры как в orbit). Hover просто scale + color change внутри filter. Blob-мёрж статичный.

### Производительность
- filter: url(#goo) — GPU-accelerated в Chromium/Electron
- requestAnimationFrame для анимаций
- will-change: transform на анимируемых элементах
- prefers-reduced-motion: убрать анимации, оставить статичный вид

### Cleanup анимаций
OrbitCircle.jsx и все компоненты с RAF:
- Хранить RAF ID в `useRef`
- `useEffect` cleanup: `cancelAnimationFrame(rafRef.current)`
- Remove event listeners при unmount
- Abort анимации при переключении табов

### Accessibility
- Каждый hit-area: `role="button"`, `aria-label="{name}, в сети/не в сети"`, `tabIndex={0}`
- `onKeyDown` для Enter/Space = click
- `:focus-visible` на hit-area: `outline: 2px solid #c8ff00; outline-offset: 3px`
- DOM порядок hit-area = визуальный порядок orbit (по часовой стрелке)
- Мини-профиль: focus trap, Escape закрывает
- Чат-лист карточки: стандартная keyboard navigation (Tab, Enter)

### Светлая тема
Все цвета через CSS custom properties:
- `--bg`: dark=#08060f, light=#f5f5f7
- `--text`: dark=rgba(255,255,255,0.85), light=rgba(26,26,46,0.85)
- `--card-alpha`: dark=0.035, light=0.06
- `--glow-alpha`: dark=0.25, light=0.15
- `--border-alpha`: dark=0.04, light=0.08
- SVG filter параметры: одинаковые для обеих тем (goo effect работает на контрасте)
- Orbit blob цвета: hue тот же, но lightness корректируется (dark=55%, light=50%)
- Фон карточек: dark=hsla(hue,60%,50%,0.035), light=hsla(hue,60%,50%,0.06)

### Typing indicator — два варианта
1. **В чате (MetaballTyping):** SVG filter, 10px dots, bounce анимация
2. **В превью чат-листа:** CSS-only, 5px dots inline, без SVG filter (слишком мелко для filter)
