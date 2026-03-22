# Chat Redesign — Design Spec

**Дата:** 2026-03-22
**Статус:** Утверждён
**Мокап:** `.superpowers/brainstorm/2842-1774175835/chat-final-assembly.html`

---

## Обзор

Полный редизайн ChatView, ChatHeader, ChatInput, ChatMessage. Новый дизайн консистентен с уже реализованными компонентами (Nebula, DynamicIsland, Glass). Все эмодзи в мокапах заменяются на Lucide React SVG иконки при реализации.

## Утверждённые решения

### 1. Сообщения — blesk Signature (Double Layer Glass)

Каждый пузырь сообщения состоит из двух вложенных стеклянных слоёв, как Glass компонент.

**Outer layer:**
- `padding: 3px`
- `backdrop-filter: blur(8px)`
- `border: 0.5px solid rgba(255,255,255,0.06)`
- `box-shadow: 0 1.5px 6px rgba(0,0,0,0.1)`
- Own: `background: rgba(200,255,0,0.03)`, `border-color: rgba(200,255,0,0.06)`, дополнительно `box-shadow: 0 0 16px rgba(200,255,0,0.025)`

**Inner layer:**
- `padding: 8px 13px`
- `backdrop-filter: blur(20px)`
- `border-top: 0.5px solid rgba(255,255,255,0.07)`
- `box-shadow: inset 0 -0.5px 1px rgba(0,0,0,0.05)`
- Specular highlight: pseudo-element `::before` — `linear-gradient(90deg, transparent, rgba(255,255,255,0.1), transparent)` по верхнему краю
- Own: `background: rgba(200,255,0,0.07)`, `border-top-color: rgba(200,255,0,0.09)`

**Группировка (радиусы):**

| Position | Other outer | Other inner | Own outer | Own inner |
|----------|-------------|-------------|-----------|-----------|
| solo | 22px | 19px | 22px | 19px |
| first | 22 22 22 6 | 19 19 19 4 | 22 22 6 22 | 19 19 4 19 |
| mid | 6 22 22 6 | 4 19 19 4 | 22 6 6 22 | 19 4 4 19 |
| last | 6 22 22 22 | 4 19 19 19 | 22 6 22 22 | 19 4 19 19 |

Gap между группами: 10px. Gap внутри группы: 3px (CSS gap на flex column).

Аватар (28px круг) показывается только у last в группе, у остальных — `visibility: hidden`.

Имя отправителя: `font-size: 11px`, `font-weight: 600`, показывается только у первого сообщения в группе.

Время: `font-size: 10px`, `color: rgba(255,255,255,0.18)`, показывается только у last в группе.

---

### 2. ChatHeader — Floating Compact Island

Капсула по центру верхней части чата, стилистически как DynamicIsland.

**Структура:** `avatar(28px) + name + dot(4px) + status + action buttons`

**Стили:**
- `padding: 6px 8px 6px 6px`
- `background: rgba(255,255,255,0.045)`
- `backdrop-filter: blur(24px)`
- `border: 0.5px solid rgba(255,255,255,0.08)`
- `border-radius: 100px`
- `box-shadow: 0 2px 12px rgba(0,0,0,0.2), inset 0 0.5px 0 rgba(255,255,255,0.06)`
- `width: fit-content`, `margin: 0 auto`

**Online dot:** 8px, `#4ade80`, `border: 2px solid #0d0b16`, absolute на аватаре.

**Action buttons:** 28px circle, `background: rgba(255,255,255,0.06)`, Lucide иконки (Phone, MoreHorizontal). Hover: `background: rgba(255,255,255,0.1)`.

**Hover на island:** `background: rgba(255,255,255,0.06)`, `transform: scale(1.02)`, transition `0.3s cubic-bezier(0.34, 1.56, 0.64, 1)`.

**Содержимое зоны header:** `padding: 10px 0`, `display: flex`, `justify-content: center`.

---

### 3. ChatInput — Morph Capsule

Два состояния: compact (свёрнут) и expanded (развёрнут).

**Compact state (когда input не в фокусе):**
- Капсула 200px по центру
- `padding: 10px 16px`
- `background: rgba(255,255,255,0.04)`
- `backdrop-filter: blur(20px)`
- `border: 0.5px solid rgba(255,255,255,0.07)`
- `border-radius: 100px`
- Текст "Написать..." (`color: rgba(255,255,255,0.25)`, `font-size: 13px`)
- Кнопка микрофона справа (28px circle)

**Expanded state (при фокусе/наборе текста):**

Двуслойный Glass, полная ширина:

*Outer:*
- `padding: 3px`
- `background: rgba(255,255,255,0.035)`
- `backdrop-filter: blur(8px)`
- `border: 0.5px solid rgba(200,255,0,0.08)` (accent glow при фокусе)
- `border-radius: 24px`
- `box-shadow: 0 0 20px rgba(200,255,0,0.04)`

*Inner:*
- `padding: 8px 6px 8px 16px`
- `background: rgba(255,255,255,0.05)`
- `backdrop-filter: blur(20px)`
- `border-radius: 21px`
- `border-top: 0.5px solid rgba(255,255,255,0.07)`
- Содержит: textarea + tool buttons (📎 😊 → Lucide: Paperclip, Smile)

**Send button:**
- 42px circle, отдельно от outer, справа
- `background: #c8ff00`, `color: #08060f`
- `box-shadow: 0 0 16px rgba(200,255,0,0.2)`
- `align-self: center` (вертикально по центру input)
- Hover: `scale(1.05)`, `box-shadow: 0 0 24px rgba(200,255,0,0.3)`
- Lucide иконка: ArrowUp

**Морф-анимация compact → expanded:**
- `transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)` (spring easing)
- Compact width → full width, border-radius 100px → 24px
- Tool buttons fade in с задержкой 0.1s

---

### 4. Hue Identity

Пузыри чужих сообщений окрашиваются в hue собеседника.

**Как работает:**
- Каждый пользователь имеет `hue` (0-360) — вычисляется из username hash
- `hue` конвертируется в HSL цвет: `hsl(hue, 60%, 65%)`
- Outer: `background: hsla(hue, 60%, 65%, 0.03)`, `border-color: hsla(hue, 60%, 65%, 0.06)`
- Inner: `background: hsla(hue, 60%, 65%, 0.06)`, `border-top-color: hsla(hue, 60%, 65%, 0.08)`
- Имя отправителя: `color: hsla(hue, 60%, 65%, 0.7)`

**Own messages:** всегда accent green (#c8ff00), не зависят от hue.

---

### 5. Send Ripple

При нажатии на кнопку отправки:
- Создаётся pseudo-element `.ripple` внутри кнопки
- `background: rgba(255,255,255,0.3)`
- Анимация: `scale(0) → scale(2.5)`, `opacity: 1 → 0`, `0.6s ease-out`
- Элемент удаляется после анимации

---

### 6. Liquid Appear (анимация появления сообщений)

Новые сообщения появляются с liquid-эффектом:

```css
@keyframes msgAppear {
  0% {
    opacity: 0;
    transform: scale(0.85) translateY(12px);
    filter: blur(4px);
  }
  60% {
    opacity: 1;
    transform: scale(1.02) translateY(-2px);
    filter: blur(0);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
    filter: blur(0);
  }
}
```

- Duration: `0.4s`
- Easing: `cubic-bezier(0.34, 1.56, 0.64, 1)`
- Применяется к каждому `msg-row` с `animation-fill-mode: both`
- Уважает `prefers-reduced-motion` — при reduced motion просто fade in без scale/blur

---

### 7. Typing Indicator (Metaball Dots)

3 точки в Double Layer Glass пузыре, окрашенном в hue собеседника.

**Bubble:**
- Outer: `background: hsla(hue, 60%, 65%, 0.03)`, radius 22px
- Inner: `background: hsla(hue, 60%, 65%, 0.06)`, radius 19px, padding 10px 16px

**Dots:**
- 7px circles, `background: hsla(hue, 60%, 65%, 0.5)`
- Gap: 4px
- Волновая анимация:

```css
@keyframes typingWave {
  0%, 60%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px) scale(1.2);
    opacity: 1;
    background: hsla(hue, 60%, 65%, 0.8);
  }
}
```

- Duration: 1.4s, infinite
- Stagger: dot2 delay 0.15s, dot3 delay 0.3s

---

## Типографика

- Сообщения: `font-family: 'Manrope'`, `font-size: 14px`, `line-height: 1.5`
- Имена: `font-size: 11px`, `font-weight: 600`
- Время: `font-size: 10px`
- Header name: `font-size: 13px`, `font-weight: 600`
- Header status: `font-size: 11px`
- Input text: `font-size: 14px`
- Input placeholder: `font-size: 13px`

---

## Адаптивность

Все размеры используют фиксированные px в контексте floating chat window. Окно чата само масштабируется через resize handles (уже реализовано). Минимальная ширина окна чата: 360px.

---

## prefers-reduced-motion

При `prefers-reduced-motion: reduce`:
- Liquid appear → простой fade in 0.2s без scale/blur
- Send ripple → отключён
- Typing dots → статичные, opacity пульсация без translateY
- Morph capsule → мгновенное переключение без spring
- Read receipt glow → без sweep, просто появление иконки
- Emoji explosion → fade in, без частиц
- Message actions pill → мгновенное появление, без scale

---

### 8. Read Receipt Glow

Вместо галочек — волна свечения проходит по сообщению когда его прочитали.

**Механика:**
- Сервер отправляет `message:read` event
- На own-сообщении запускается анимация:

```css
@keyframes readGlow {
  0% {
    box-shadow: inset -100% 0 0 0 rgba(200,255,0,0.08);
  }
  50% {
    box-shadow: inset 0 0 20px rgba(200,255,0,0.06);
  }
  100% {
    box-shadow: none;
  }
}
```

- Pseudo-element `::after` на `.bubble-outer`: горизонтальный gradient sweep слева направо
- Duration: `0.8s ease-out`, одноразовая
- После анимации — тонкая точка или мини-иконка (Lucide: CheckCheck, 10px) в углу пузыря, `color: rgba(200,255,0,0.3)`
- `prefers-reduced-motion`: без sweep, просто появление иконки

---

### 9. Emoji Explosion

Если сообщение содержит ТОЛЬКО эмодзи (1-3 штуки), они отображаются крупно и "взрываются" при появлении.

**Отображение:**
- Без пузыря (no bubble-outer/inner) — просто большой эмодзи
- 1 эмодзи: `font-size: 48px`
- 2 эмодзи: `font-size: 36px`
- 3 эмодзи: `font-size: 28px`
- 4+ эмодзи или смесь с текстом: обычный пузырь

**Анимация explosion:**
```css
@keyframes emojiExplode {
  0% { transform: scale(0.3); opacity: 0; }
  50% { transform: scale(1.3); }
  70% { transform: scale(0.95); }
  100% { transform: scale(1); opacity: 1; }
}
```
- Duration: `0.5s cubic-bezier(0.34, 1.56, 0.64, 1)`
- 6-8 мелких частиц (3px circles, цвет accent) разлетаются от центра и гаснут за 0.6s
- Частицы: position absolute, radial directions, `opacity: 1 → 0`, `scale: 1 → 0`
- `prefers-reduced-motion`: простой fade in, без частиц

---

### 10. Date Separator Pill

Разделитель дат между сообщениями из разных дней.

**Стиль:**
- Glass капсула по центру: `padding: 4px 14px`, `border-radius: 100px`
- `background: rgba(255,255,255,0.04)`, `backdrop-filter: blur(16px)`
- `border: 0.5px solid rgba(255,255,255,0.06)`
- Текст: `font-size: 11px`, `font-weight: 500`, `color: rgba(255,255,255,0.35)`
- `margin: 16px auto`

**Sticky behavior:**
- При скролле вверх, текущий separator прилипает к верху messages-области
- `position: sticky`, `top: 0`, `z-index: 5`
- При прилипании — усиление blur: `backdrop-filter: blur(24px)`

**Формат текста:**
- Сегодня → "Сегодня"
- Вчера → "Вчера"
- Этот год → "15 марта"
- Прошлый год → "15 марта 2025"

---

### 11. Message Actions Pill

При hover на сообщении — стеклянная капсула с действиями появляется рядом.

**Расположение:**
- Own messages: появляется слева от пузыря
- Other messages: появляется справа от пузыря
- Вертикально: по центру сообщения

**Стиль капсулы:**
- `background: rgba(255,255,255,0.06)`, `backdrop-filter: blur(20px)`
- `border: 0.5px solid rgba(255,255,255,0.08)`
- `border-radius: 100px`
- `padding: 3px`
- `box-shadow: 0 2px 12px rgba(0,0,0,0.2)`

**Кнопки (Lucide icons, 26px circles):**
- Reply (CornerUpLeft)
- React (SmilePlus) — открывает emoji picker
- Edit (Pencil) — только для own
- Delete (Trash2) — только для own
- `color: rgba(255,255,255,0.5)`, hover: `background: rgba(255,255,255,0.08)`, `color: rgba(255,255,255,0.8)`

**Анимация появления:**
- `opacity: 0 → 1`, `scale(0.8) → scale(1)`, `0.15s ease-out`
- Исчезает при mouse leave с `0.1s`

---

### 12. Unread Divider

Стеклянная полоска между прочитанными и новыми сообщениями.

**Стиль:**
- Горизонтальная линия + текст по центру
- Линия: `height: 1px`, `background: linear-gradient(90deg, transparent, rgba(200,255,0,0.15), transparent)`
- Текст "Новые сообщения": `font-size: 11px`, `font-weight: 600`, `color: rgba(200,255,0,0.5)`
- Фон за текстом: `background: var(--bg)`, `padding: 0 12px` (перекрывает линию)
- `margin: 12px 0`

**Поведение:**
- Появляется при открытии чата с непрочитанными
- Исчезает (fade out) через 5 секунд после того как пользователь доскроллил до неё
- Или при отправке своего сообщения

---

### 13. Reply Thread Line

Тонкая линия соединяющая ответ с оригинальным сообщением.

**Стиль:**
- `width: 2px`, `border-radius: 1px`
- Цвет: `rgba(200,255,0,0.12)` для own, `hsla(hue, 60%, 65%, 0.12)` для other
- Вертикальная линия от цитаты вверх до оригинала
- С закруглением на поворотах (border-radius на углах)

**Quote preview в пузыре:**
- Accent bar слева: `3px`, цвет отправителя оригинала
- Имя: `font-size: 11px`, `font-weight: 600`, цвет hue
- Текст: `font-size: 12px`, `color: rgba(255,255,255,0.4)`, max 1 строка (ellipsis)
- Клик на цитату → smooth scroll к оригиналу + кратковременная подсветка оригинала

---

### 14. Link Preview Cards

URL в сообщениях отображаются как мини glass-карточки.

**Стиль карточки:**
- Внутри пузыря, ниже текста сообщения
- `margin-top: 8px`
- `border-radius: 14px`
- `background: rgba(255,255,255,0.04)`
- `border: 0.5px solid rgba(255,255,255,0.06)`
- `overflow: hidden`

**Содержимое:**
- Image preview сверху (если есть og:image): `height: 120px`, `object-fit: cover`
- Title: `font-size: 13px`, `font-weight: 600`, max 2 строки
- Description: `font-size: 12px`, `color: rgba(255,255,255,0.4)`, max 2 строки
- Domain: `font-size: 11px`, `color: rgba(255,255,255,0.25)`, с иконкой Globe (Lucide)
- `padding: 10px`

**Реализация:** Сервер фетчит og:title, og:description, og:image при получении сообщения с URL. Результат кэшируется. Клик на карточку → открывает URL в системном браузере.

---

### 15. Voice Message Waveform (будущее, v0.6+)

Голосовые сообщения отображаются как Glass-пузырь с живой волной.

**Стиль:**
- Glass bubble (Double Layer) как обычное сообщение
- Внутри: кнопка play (20px circle, accent) + waveform canvas + duration
- Waveform: 40-60 вертикальных полосок (2px wide, gap 1px)
- Высота полосок = амплитуда аудио
- Цвет: `rgba(255,255,255,0.25)` (не проигранные), accent `rgba(200,255,0,0.6)` (проигранные)
- При воспроизведении — прогресс слева направо, полоски меняют цвет

**Примечание:** Требует backend поддержки записи/хранения аудио. Отложено до v0.6+, но дизайн закреплён.

---

## Не включено в этот редизайн (отложено)

- Thread Branches (ответы как ветки)
- Timeline Scroll (горизонтальная полоска дат)
- Pinned Layer (стеклянная полка закреплённых)
- Presence Bar (кто в чате сейчас)
- Sound Ripples (волны вокруг говорящего)
- Breathing UI (пульсация при idle)
