# Profile System Redesign — Design Spec

**Date:** 2026-04-02
**Status:** Approved
**Approach:** Full rewrite (Approach B)

---

## Overview

Полная переделка системы профилей blesk. Новая компонентная архитектура из 5 компонентов вместо текущих разрозненных ProfileScreen, UserProfileModal, StatusEditor.

**Ключевые решения:**
- Чужой профиль: popover у аватара (не модалка по центру)
- Свой профиль: ProfileCard (mode own) + отдельный ProfileEditor
- Будущие фичи: placeholder-секции "Скоро" (теги, коллекция)
- Banner: размытый аватар (blur 40px)
- Статус: segmented circle (радиальное меню вокруг точки)
- Avatar lightbox: клик на аватар = увеличение на весь экран

---

## Component Architecture

```
components/profile/
├── ProfileCard.jsx        # Единая карточка (mode: 'own' | 'other')
├── ProfileCard.css
├── ProfilePopover.jsx     # Позиционер — оборачивает ProfileCard для popover
├── ProfileEditor.jsx      # Полноэкранный редактор профиля
├── ProfileEditor.css
├── UserBadge.jsx          # Микрокомпонент: аватар + имя + статус
├── UserBadge.css
└── AvatarCropModal.jsx    # Остаётся как есть
```

**Удаляются:**
- `ProfileScreen.jsx` + `ProfileScreen.css` → заменяется ProfileCard + ProfileEditor
- `UserProfileModal.jsx` + `UserProfileModal.css` → заменяется ProfilePopover + ProfileCard
- `StatusEditor.jsx` + `StatusEditor.css` → встраивается в ProfileEditor

---

## 1. ProfileCard

Единый компонент для своего и чужого профиля.

**Visual structure (top to bottom):**

```
┌─────────────────────────────────┐
│░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│  banner 80px: аватар blur(40px)
│░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│  + overlay rgba(0,0,0,0.4)
│         ┌─────────┐             │  + gradient снизу → card bg
│         │  avatar  │ ← lightbox │  аватар 96px, margin-top -48px
│         └─────────┘             │  ring с hue glow, online pulse
│        username                  │  var(--font-display) Nekst, 22px bold
│         #0000                    │  14px, text-tertiary
│                                 │
│  ● В сети          ✎ (own only) │  статус + edit button
│  "кастомный статус"             │  italic, если есть
│        био                       │  15px, text-secondary, max 3 строки
│                                 │
│ ┌─ Теги ────────────────────┐   │  placeholder: Lock icon + "Скоро"
│ │  Скоро                    │   │  glass-inner, text-disabled
│ └───────────────────────────┘   │  shimmer при hover
│ ┌─ Коллекция ───────────────┐   │
│ │  Скоро                    │   │
│ └───────────────────────────┘   │
│                                 │
│  ◎ На blesk с марта 2026        │  Calendar icon, chip
│                                 │
│  ┌─────────────────────────┐    │  primary action:
│  │    ◯ Написать            │    │  other: "Написать"/"Добавить"
│  └─────────────────────────┘    │  own: "Редактировать"
└─────────────────────────────────┘
```

**Width:** 340px fixed.

**Banner details:**
- Аватар пользователя растянут на всю ширину + `filter: blur(40px)` + тёмный overlay
- Если аватара нет → fallback на hue gradient
- Clip: наследует border-radius верхних углов карточки
- Overlay: linear-gradient снизу (transparent → card background) для плавного перехода
- Параллакс: лёгкий сдвиг при mousemove (2px max)
- При загрузке: blur(60px) → blur(40px) за 300ms (эффект фокусировки)

**Avatar details:**
- 96px, ring = rotating conic-gradient (hue пользователя)
- Online: green glow shadow + пульсация (scale 1→1.05, 3s loop)
- Клик → avatar lightbox (shared layout animation)
- Если аватара нет → клик не работает
- z-index выше banner

**Avatar lightbox:**
- `layoutId="avatar-{userId}"` — shared layout animation
- Аватар растёт до 320px по центру экрана
- Backdrop: `rgba(0,0,0,0.7)` + `blur(8px)`
- Показывает полное разрешение (оригинал с сервера)
- Закрытие: клик на backdrop / Escape
- z-index: `var(--z-modal)` (1000)
- Debounce 200ms для быстрых двойных кликов
- Если аватар > 5МБ: skeleton пока грузится

**Месяцы склоняются правильно:**
январь→января, февраль→февраля, март→марта, апрель→апреля, май→мая, июнь→июня, июль→июля, август→августа, сентябрь→сентября, октябрь→октября, ноябрь→ноября, декабрь→декабря

**Mode differences:**

| Element | `own` | `other` |
|---------|-------|---------|
| Edit button (PencilSimple) | Да | Нет |
| Primary action | "Редактировать" | "Написать" / "Добавить в друзья" / "Запрос отправлен" |
| Данные | `user` prop из MainScreen (без API) | GET /api/users/:id |

**Loading states:**
- Skeleton: 3 пульсирующих прямоугольника (banner + avatar circle + 3 text lines)
- Ошибка: Warning icon + "Не удалось загрузить" + кнопка "Повторить"
- Transition загрузка → данные → ошибка: crossfade 150ms

**Edge cases:**
- userId не существует → skeleton 300ms → "Пользователь не найден"
- Сеть упала → retry button
- Длинный ник (32 символа) → `text-overflow: ellipsis`, 1 строка
- Длинный bio → max 3 строки, ellipsis
- Custom status пустой → секция скрыта
- Свой профиль в чате → `mode="own"`, данные из `user` prop
- Забаненный пользователь → карточка с Warning icon + "Пользователь заблокирован", без кнопок действий
- `lastSeenAt` равен null (приватность) → показать только "Не в сети" без времени

**API (mode other):**
- `GET /api/users/:id` — загрузка профиля (возвращает `isFriend: boolean`)
- `POST /api/friends/request` с body `{ userId }` — отправить запрос дружбы
- Кнопка "Написать": вызывает callback `onOpenChat(userId)` (не прямой API вызов — делегирует родителю)
- Кнопка "Добавить в друзья" / "Запрос отправлен": определяется по `isFriend` из ответа API + локальный state для pending
- Online status: `chatStore.userStatuses[userId]`
- Last seen: форматирование `lastSeenAt` с учётом `showLastSeen`

**Props (updated):**
```
mode: 'own' | 'other'     — режим отображения
userId: string             — ID пользователя
user: object               — данные текущего юзера (для mode own, из MainScreen)
onEdit: () => void         — callback кнопки "Редактировать" (own)
onOpenChat: (userId) => void — callback кнопки "Написать" (other)
onClose: () => void        — callback закрытия
```

---

## 2. ProfilePopover

Обёртка-позиционер для ProfileCard.

**Props:**
```
anchorRef: RefObject       — элемент-якорь
userId: string             — чей профиль
onClose: () => void        — callback закрытия
isOpen: boolean            — управление видимостью
```

**Rendering:**
- React Portal к `document.body` (z-index изоляция от родительских stacking contexts)
- Позволяет lightbox внутри popover работать корректно (z-index modal > dropdown)

**Positioning:**
- По умолчанию: справа от anchor, отступ 8px
- Не влезает справа → слева
- Не влезает ни туда → центр экрана (fallback)
- Всегда внутри viewport с padding 16px от краёв
- `z-index: var(--z-dropdown)` (60)

**Animations:**
- Появление: spring scale 0.92→1 + opacity 0→1, transform-origin = сторона anchor
- Закрытие: 120ms opacity 1→0 + scale 1→0.96
- Framer Motion `AnimatePresence`

**Backdrop:**
- Прозрачный overlay (не затемняющий) — только для перехвата кликов
- Без blur

**Closing triggers:**
- Клик вне popover
- Escape
- Клик на другой аватар (закрыть текущий → открыть новый)
- Переход на другой экран
- Входящий звонок
- Ctrl+K (spotlight)

**Edge cases:**
- Resize окна → закрытие popover
- Скролл родителя → закрытие popover
- Быстрый двойной клик на разные аватары → debounce, закрыть первый перед вторым
- Аватар в самом низу экрана → popover сверху
- Клик на свой аватар в сообщении → `mode="own"`
- Пользователь забанен → fallback "Пользователь недоступен"

---

## 3. ProfileEditor

Полноэкранная панель редактирования (как Settings).

**Layout:**
```
┌─────────────────────────────────────┐
│  ← Назад          Профиль          │  header с кнопкой назад
│─────────────────────────────────────│
│                                     │
│  ┌───────────┐                      │
│  │  avatar    │  96px, клик =       │
│  │  + camera  │  AvatarCropModal    │
│  └───────────┘                      │
│  username #0000                      │
│  с марта 2026                        │
│                                     │
│  ─── Основное ───────────────────── │
│                                     │
│  ┌─ НИКНЕЙМ ─────────────────────┐  │
│  │ gotblesk                      │  │  input, inline edit
│  └───────────────────────────────┘  │
│                                     │
│  ┌─ EMAIL ───────────────────────┐  │
│  │ ✉ mxkarov@mail.ru  ● верифиц │  │  expandable cell
│  └───────────────────────────────┘  │
│                                     │
│  ┌─ ТЕЛЕФОН ─────────────────────┐  │
│  │ 📱 Не привязан                │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌─ ПАРОЛЬ ──────────────────────┐  │
│  │ 🔒 ●●●●●●●●   Изменить →     │  │  expandable cell
│  └───────────────────────────────┘  │
│                                     │
│  ─── О себе ─────────────────────── │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ textarea...          124/200  │  │  counter → orange >180
│  └───────────────────────────────┘  │
│                                     │
│  ─── Статус ─────────────────────── │
│                                     │
│  ● В сети  ● Не беспокоить  ● Невид│  preset buttons
│                                     │
│  ┌───────────────────────────────┐  │
│  │ кастомный статус...    32/50  │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌─────────────────────────────┐    │
│  │        Сохранить             │    │  accent button, disabled if clean
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Behavior:**
- Открывается: кнопка "Редактировать" на ProfileCard (own)
- "Назад" → возврат к предыдущему экрану
- "Сохранить" активна только при dirty state
- Unsaved changes + навигация → confirm dialog
- Expandable cells: motion.div height auto, overflow hidden during transition
- Валидация inline: красный текст под полями
- Успех: toast "Профиль обновлён"

**Keyboard:**
- Tab order: avatar → nickname → email → phone → password → bio → status → save
- Ctrl+S = сохранить
- Escape = назад (с confirm если dirty)
- Без auto-focus при открытии

**Validation:**
- Ник: 2-32 символа, латиница + кириллица + цифры + `_.-`, debounce 500ms проверка уникальности
- Bio: 200 символов, counter color change >180
- Custom status: 50 символов

**API endpoints:**
- `PUT /api/users/me` — bio, username, status, customStatus
- `POST /api/users/me/avatar` — аватар (FormData)
- `POST /api/auth/resend-code` — запрос кода верификации email
- `POST /api/auth/verify-email` — верификация email
- `POST /api/auth/change-password/request` — запрос смены пароля
- `POST /api/auth/change-password/confirm` — подтверждение смены

**Style:**
- Double layer glass панель
- Секции: label text-tertiary, uppercase, 12px
- Ячейки: glass-inner с Phosphor icon слева
- Scrollable при overflow

---

## 4. TopNav Dropdown — Redesign

**Width:** 260px (было 240px).

**Layout:**
```
┌───────────────────────────────┐
│                               │
│  ┌────┐  gotblesk             │  аватар 44px + имя
│  │ av │  #0000                │  tag
│  └────┘  ● В сети  ← кликабл │  segmented circle trigger
│                               │
│  ─────────────────────────── │
│                               │
│  👤 Профиль                   │  → ProfileCard popover (own)
│  ⚙ Настройки                  │  → Settings screen
│                               │
│  ─────────────────────────── │
│                               │
│  ↪ Выйти                     │  danger red
│                               │
└───────────────────────────────┘
```

**Segmented circle (status switcher):**
- Рядом с именем — статус-точка 12px, цвет текущего статуса
- Клик → точка увеличивается до 30px
- Вокруг появляются 3 цветных сегмента кольцом:
  - Верх-лево: зелёный (В сети)
  - Верх-право: красный (Не беспокоить)
  - Низ: серый (Невидимка)
- Каждый сегмент: дуга ~100° с gap
- Hover на сегмент → подсветка + tooltip ("В сети" / "Не беспокоить" / "Невидимка")
- Клик на сегмент → точка меняет цвет (crossfade 150ms), кольцо collapse
- Текущий статус: accent border на сегменте
- Если dropdown закрыт пока кольцо открыто → кольцо collapse (100ms) → dropdown close

**Segmented circle animation flow:**
1. Точка 12px (idle)
2. Клик → scale до 30px (150ms spring)
3. 3 дуги staggered 50ms от центра (spring: stiffness 300, damping 25)
4. Выбор → выбранная дуга pulse → shrink в центр, точка меняет цвет
5. Остальные дуги fade 100ms
6. Точка shrink до 12px

**Accessibility:**
- `role="radiogroup"`, каждый сегмент `role="radio"` + `aria-label`
- Keyboard: Tab → Enter раскрывает → Arrow keys → Enter выбирает

**Icons:** User (Профиль), GearSix (Настройки), SignOut (Выйти) — Phosphor.

---

## 5. UserBadge

Переиспользуемый микрокомпонент.

**Props:**
```
userId: string              — ID пользователя
size: 'sm' | 'md' | 'lg'   — размер
showStatus: boolean         — точка онлайн (default true)
showCustomStatus: boolean   — текст статуса (default false)
clickable: boolean          — клик → ProfilePopover (default true)
subtitle: string            — переопределить вторую строку
```

**Sizes:**

| Size | Avatar | Name font | Second line | Usage |
|------|--------|-----------|-------------|-------|
| `sm` | 24px | 13px | нет | упоминания, мелкие списки |
| `md` | 36px | 15px | custom status / subtitle, 13px | друзья, участники |
| `lg` | 48px | 17px | custom status, 14px | header чата, sidebar |

**Behavior:**
- `clickable=true` → cursor pointer, hover glass bg, клик → ProfilePopover
- Ellipsis на длинном имени + `title` tooltip
- Данные из chatStore кеша, без API запросов
- `React.memo` с comparator (userId + avatar + status)
- Подписка на статус только при `showStatus=true`

**Replaces manual avatar+name rendering in:**
- FriendsScreen (список друзей)
- Voice room participants
- Chat header (имя собеседника)
- Group member list
- User search results

---

## 6. Animations & Transitions

**ProfileCard (popover):**
- Appear: spring scale 0.92→1 + opacity, transform-origin = anchor side
- Children: staggered 40ms delay each
- 3D tilt: 4° max on mousemove, smooth lerp
- Close: 120ms opacity + scale 1→0.96 (no stagger)

**Avatar lightbox:**
- Shared layout animation (`layoutId`)
- Backdrop fade 200ms
- Close: reverse animation, avatar returns

**Banner:**
- Load: blur(60px) → blur(40px) in 300ms
- Parallax: 2px max on mousemove

**Segmented circle:**
- Open: spring (stiffness 300, damping 25), staggered arcs 50ms
- Select: pulse → shrink, color crossfade 150ms

**ProfileEditor:**
- Open: slide-in from right (translateX 100% → 0, 300ms ease-glass)
- Expandable cells: spring height auto
- Save button: shake on error, pulse on success

**UserBadge:**
- Hover: glass bg fade 100ms
- Click: scale 0.97→1 tactile

**Reduced motion:**
- All animations → instant opacity transitions
- No 3D tilt, no parallax, no spring
- `prefers-reduced-motion: reduce` + settingsStore `performanceMode: 'eco'`

---

## 7. Shared Concerns

**Light theme:** все glass значения через CSS variables, инвертируются автоматически.

**Focus ring:** 2px solid accent, WCAG 2.4.13 compliant (из global.css).

**All colors:** через CSS variables, никаких хардкодов.

**data-testid:** на всех интерактивных элементах.

**Phosphor icons used:**
- PencilSimple (edit), User (profile), GearSix (settings), SignOut (logout)
- ChatCircle (написать), UserPlus (добавить), Check (запрос отправлен)
- Calendar (дата), Clock (last seen), Lock (placeholder sections)
- Camera (avatar upload), Eye/EyeSlash (password), CaretRight (expand cell)
- Envelope (email), DeviceMobile (phone), Warning (error state), ArrowLeft (назад)

---

## 8. Consumer Migration Plan

Файлы которые импортируют удаляемые компоненты и что с ними делать:

**UserProfileModal.jsx → ProfilePopover + ProfileCard:**

| File | Current usage | Migration |
|------|--------------|-----------|
| `FriendsScreen.jsx` | `<UserProfileModal>` при клике на друга | Заменить на `<ProfilePopover>` с anchorRef на аватар друга |
| `VoiceRoom.jsx` | `<UserProfileModal>` при клике на участника | Заменить на `<ProfilePopover>` |
| `ChatView.jsx` | `<UserProfileModal>` при клике на аватар в сообщении | Заменить на `<ProfilePopover>` |
| `GroupMembersPanel.jsx` | `<UserProfileModal>` при клике на участника группы | Заменить на `<ProfilePopover>` |

**ProfileScreen.jsx → ProfileCard + ProfileEditor:**

| File | Current usage | Migration |
|------|--------------|-----------|
| `MainScreen.jsx` | `<ProfileScreen>` как отдельный экран | Заменить на `<ProfileEditor>` (полноэкранная панель). ProfileCard (own) показывается через ProfilePopover при клике на аватар в TopNav dropdown |

**StatusEditor.jsx → ProfileEditor:**

| File | Current usage | Migration |
|------|--------------|-----------|
| `MainScreen.jsx` | `<StatusEditor>` как модалка | Удалить — статус встроен в ProfileEditor + segmented circle в TopNav dropdown |

**Manual avatar+name rendering → UserBadge:**

| File | Current rendering | Migration |
|------|------------------|-----------|
| `FriendsScreen.jsx` | `<Avatar>` + `<span>{name}</span>` | `<UserBadge userId={id} size="md" clickable />` |
| `VoiceRoom.jsx` | avatar + name inline | `<UserBadge userId={id} size="md" />` |
| `ChatView.jsx` header | avatar + name | `<UserBadge userId={id} size="lg" />` |
| `GroupMembersPanel.jsx` | avatar + name list | `<UserBadge userId={id} size="md" />` |
| `SpotlightSearch.jsx` | user results | `<UserBadge userId={id} size="sm" />` |

**Navigation flow (TopNav → Profile):**
1. Клик на аватар в TopNav → dropdown открывается
2. "Профиль" в dropdown → dropdown закрывается → ProfilePopover с ProfileCard (mode own) появляется у аватара TopNav
3. "Редактировать" на ProfileCard → popover закрывается → ProfileEditor (slide-in) как основной контент в MainScreen

---

## 9. data-testid Convention

Формат: `kebab-case`, компонент-элемент:

```
profile-card                    — корень ProfileCard
profile-card-banner             — banner
profile-card-avatar             — аватар (кликабельный)
profile-card-username           — имя
profile-card-edit-btn           — кнопка редактирования
profile-card-action-btn         — primary action (написать/добавить/редактировать)
profile-card-tags-placeholder   — секция тегов
profile-card-collection-placeholder — секция коллекции

profile-popover                 — корень ProfilePopover
profile-popover-backdrop        — прозрачный backdrop

profile-editor                  — корень ProfileEditor
profile-editor-back-btn         — кнопка назад
profile-editor-avatar           — аватар с камерой
profile-editor-nickname-input   — поле ника
profile-editor-email-cell       — ячейка email
profile-editor-phone-cell       — ячейка телефона
profile-editor-password-cell    — ячейка пароля
profile-editor-bio-input        — textarea bio
profile-editor-status-preset    — пресет статуса (с data-status="online|dnd|invisible")
profile-editor-custom-status    — поле кастомного статуса
profile-editor-save-btn         — кнопка сохранить

user-badge                      — корень UserBadge
user-badge-avatar               — аватар в badge

avatar-lightbox                 — корень lightbox
avatar-lightbox-backdrop        — backdrop
avatar-lightbox-image           — увеличенный аватар

topnav-user-menu                — dropdown меню
topnav-status-dot               — точка статуса (trigger segmented circle)
topnav-status-segment           — сегмент кольца (с data-status)
```
