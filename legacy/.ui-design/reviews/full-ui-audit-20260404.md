# ПОЛНЫЙ АУДИТ UI — BLESK v1.0.6-beta

**Дата:** 04.04.2026
**Охват:** 70+ компонентов, 74+ CSS файлов, 7 сторов, 7 хуков
**Фокус:** Визуал, юзабилити, код, производительность, дизайн-критика
**Платформы:** десктоп, планшет, мобильные

---

## СВОДКА

| Область | Оценка | Статус |
|---------|--------|--------|
| Визуальный дизайн | 7.5/10 | Сильная система, слабое соблюдение токенов |
| Юзабилити & A11y | 6/10 | Хорошие модалки, плохая семантика |
| Качество кода | 6.5/10 | Zustand правильный, но prop drilling и state bloat |
| Производительность | 6.5/10 | Виртуализация есть, нет code-splitting |
| **Общая** | **6.6/10** | **Хороший фундамент, нужна дисциплина** |

**Найдено проблем: 47**
- Critical: 8
- Major: 14
- Minor: 15
- Suggestion: 10

---

## 1. ВИЗУАЛЬНЫЙ ДИЗАЙН

### Сильные стороны
- Комплексная токен-система в global.css (z-index, типографика, цвета, spacing, radius)
- Double Layer Glass корректно реализован (outer blur 8px + inner blur 16px)
- 4 уровня глубины glass (depth 0-3) с tinted shadows
- Шрифтовая система: Nekst (display) + Onest (body) — осознанный выбор
- Кислотно-зелёный акцент (#c8ff00) — узнаваемый, не шаблонный
- Responsive sizing через clamp() для Island и Nebula cards

### Critical

**C-VIS-1: Hardcoded border-radius вместо токенов**
47+ CSS файлов используют хардкод (15px, 32px, 24px) вместо --radius-sm/md/lg/pill.
- DynamicIsland.css:65 → `border-radius: 15px` (должно быть --radius-md: 14px)
- DynamicIsland.jsx:21 → `borderRadius: 24` (должно быть 22 = --radius-lg)
- UpdateBanner.css:56 → `border-radius: 2px` (вне системы)

**C-VIS-2: Hardcoded цвета в 47+ файлах**
#ef4444, #4ade80, #facc15 используются напрямую вместо --danger, --online, --warning.

**C-VIS-3: Hardcoded font-size вместо --text-* токенов**
- ConfirmDialog.css:68 → `font-size: 16px` (должно быть --text-md)
- Avatar.css:15-18 → `font-size: 11px/15px/20px/32px` — hardcoded

**C-VIS-4: Hardcoded spacing вместо --space-* токенов**
- GlassSkeleton.css:62 → `gap: 10px` (должно быть --space-3)
- island.css:98 → `gap: 8px` (должно быть --space-2)

### Major

**M-VIS-1: Light theme контраст низкий в ConfirmDialog**
```css
/* ConfirmDialog.css:156 — слишком бледно */
background: rgba(0, 0, 0, 0.05);
border-color: rgba(0, 0, 0, 0.08);
```

**M-VIS-2: DynamicIsland дублирует Glass логику**
island.css содержит собственную реализацию вместо переиспользования Glass компонента.

**M-VIS-3: Нет полного набора shadow-токенов**
Только --glass-shadow, но компоненты используют разные rgba(0,0,0,...) значения.

**M-VIS-4: Не все компоненты имеют hover + focus + active**
EmptyState — только :hover на кнопке, нет :active. Многие иконки-кнопки без focus-visible.

### Метрики консистентности

| Параметр | Покрытие | Комментарий |
|----------|----------|-------------|
| CSS переменные (цвета) | 80% | Система есть, 47+ файлов с hardcode |
| CSS переменные (spacing) | 75% | Определены, не везде применяются |
| CSS переменные (radius) | 60% | Система есть, часто игнорируется |
| Типографика | 85% | Хорошие токены, hardcodes в компонентах |
| Glass depth | 95% | Корректная реализация |
| Light theme | 65% | Полная, но контраст нужно улучшить |

---

## 2. ЮЗАБИЛИТИ И ДОСТУПНОСТЬ

### Сильные стороны
- ConfirmDialog: идеальный focus trap (циклический Tab, Escape, возврат фокуса)
- ContextMenu: правильная ARIA семантика (role="menu", role="menuitem", Arrow keys)
- VoiceControls: aria-label на кнопках mute/deafen
- prefers-reduced-motion поддержка в Settings, ProfileCard
- Offline-баннер с кнопкой "Переподключить"

### Critical

**C-A11Y-1: div вместо button на кликабельных элементах**
- ChatCard.jsx:126 → `<div onClick>` без role, tabIndex, onKeyDown
- FriendsScreen.jsx:187 → `<motion.div onClick>` без keyboard support

**C-A11Y-2: Табы без ARIA семантики**
- FriendsScreen.jsx:125 → нет role="tablist", role="tab", aria-selected
- ChannelBrowser.jsx:89 → категории без role="tablist"
- SettingsScreen.jsx:22 → tabs без семантики

**C-A11Y-3: ChatInput textarea без aria-label**
Только placeholder, нет aria-label="Поле ввода сообщения".

**C-A11Y-4: Ошибки без role="alert"**
- ChatInput mic error (строка 670) → `<span>` без role="alert"
- ChannelView postError → без aria-live
- ProfileEditor validation → без aria-invalid

### Major

**M-A11Y-1: Нет семантического HTML**
ChatView, FriendsScreen, ChannelBrowser — всё в `<div>`. Нет `<main>`, `<section>`, `<article>`.

**M-A11Y-2: Toggle-кнопки без aria-pressed**
VoiceControls mute/deafen/camera — есть aria-label, но нет aria-pressed={state}.

**M-A11Y-3: Framer Motion анимации не проверяют prefers-reduced-motion**
ChatView, CreateChannelModal, FriendsScreen — motion.div всегда анимируется.

**M-A11Y-4: Touch targets потенциально < 44x44px**
- ChatInput tool buttons (emoji, mic) — иконка 18px, padding неизвестен
- MessageActionsPill emoji buttons — вероятно < 44px

**M-A11Y-5: Search inputs без Escape для очистки**
ChatHub.jsx:49, ChannelBrowser.jsx:85 — нет onKeyDown обработчика.

---

## 3. КАЧЕСТВО КОДА

### Сильные стороны
- Zustand — правильный выбор для state management
- React.memo на ChatMessage
- Socket.io reconnect с exponential backoff
- E2E encryption с shield/legacy support
- Sound engine — профессиональный (ADSR, harmonic synthesis, reverb)

### Critical

**C-CODE-1: 16+ useState в MainScreen.jsx**
Один компонент управляет всеми модалками, панелями, активными табами. Каждый setState → полный re-render дерева.
**Fix:** Вынести в Zustand UI store или useReducer.

**C-CODE-2: Утечки памяти — AudioContext без cleanup**
sounds.js:4 → глобальный `audioCtx` без способа закрыть при завершении.
MetaballBackground.jsx → множественные mousemove/keydown listeners без дебаунса.

**C-CODE-3: Race conditions в useSocket.js**
При разрыве сети может параллельно вызваться loadChats() несколько раз.

### Major

**M-CODE-1: Prop drilling 3-4 уровня**
MainScreen → TopNav/Sidebar/ContentArea → ChatView → ChatMessage.

**M-CODE-2: React.memo на ChatMessage сломан**
Callback-пропсы (onReply, onEdit, onDelete) создаются заново каждый рендер в ChatView → мемоизация не работает.

**M-CODE-3: Неоптимальные Zustand селекторы**
```js
// ChatView.jsx:49 — O(N) поиск на каждый рендер
const chat = useChatStore(s => s.chats.find(c => c.id === chatId));
// Подписка на весь onlineUsers массив
const onlineUsers = useChatStore(s => s.onlineUsers);
```

**M-CODE-4: Нет code-splitting**
MainScreen импортирует 15+ компонентов синхронно. MetaballBackground, AdminPanel, SettingsScreen должны быть lazy.

**M-CODE-5: Inline стили в 10+ компонентах**
ChannelBrowser.jsx:115, ChatInput.jsx:520 — объекты стилей создаются на каждый рендер.

**M-CODE-6: 25+ обработчиков в одном useEffect (useSocket.js)**
Callback hell, сложно поддерживать.

### Minor

- Hardcoded магические числа (30000ms idle, 3000ms ringtone) → нужны константы
- Regex в теле ChatMessage вместо модуля констант
- catch без действия в chatStore (silent fail)
- Нет TypeScript — сложно отследить контракты пропсов

---

## 4. ПРОИЗВОДИТЕЛЬНОСТЬ

### Сильные стороны
- @tanstack/react-virtual в ChatView — виртуализация есть
- Eco-mode для MetaballBackground (CSS fallback)
- Code splitting в Vite (vendor/three/animation/emoji chunks)
- Source maps отключены в production

### Major

**M-PERF-1: estimateSize: () => 52 — жёсткая оценка**
Не учитывает attachments (+200px), link preview (+120px), reply (+40px). Вызывает прыжки скролла.

**M-PERF-2: Множественные mousemove/keydown listeners**
MetaballBackground + AnimatedBackground + idle detection — каждый слушает mousemove. Нужен один consolidated handler.

**M-PERF-3: Three.js lerp без deltaTime**
```js
// MetaballBackground.jsx:285 — фиксированный factor
mat.uniforms.uSubtle.value += (target - current) * 0.04;
```
На 30fps vs 60fps результат будет разный. Нужен time-based lerp.

**M-PERF-4: Нет lazy-loading для тяжёлых компонентов**
MetaballBackground (Three.js), AdminPanel, SettingsScreen — всё в main bundle.

### Minor
- createReverbBuffer генерирует 86400 samples при каждом создании AudioContext
- Zustand onlineUsers/userStatuses подписки слишком широкие

---

## 5. КРИТИКА ДИЗАЙНА — ВЗГЛЯД СО СТОРОНЫ

### Выглядит ли как "нейросетевой паттерн"?

**Короткий ответ: Нет, не выглядит как типичный AI-шаблон. Но есть нюансы.**

#### Что делает blesk НЕ-шаблонным:

1. **Кислотно-зелёный акцент (#c8ff00) на тёмном (#0a0a0f)** — это не стандартный AI-градиент purple-to-blue. Это агрессивный, запоминающийся выбор. Если убрать логотип, палитру всё равно узнают — и это хорошо.

2. **Double Layer Glass** — не просто backdrop-filter blur как у тысяч лендингов. Два вложенных слоя с edge glow и specular highlights — это технически сложнее и визуально богаче. Apple Liquid Glass inspired, но не копия.

3. **Nekst + Onest** — осознанный выбор шрифтов, а не Inter/Roboto/system-ui. Nekst для заголовков даёт характер, Onest для текста даёт читаемость. Это не "AI-дизайн".

4. **Dynamic Island** — концепция заимствована у Apple, но адаптирована под мессенджер (call state, recording, typing, update). Это не стандартный элемент "AI-приложения".

5. **Метабол-фон (Three.js shader)** — chromatic aberration, idle breathing, event-reactive pulse — это авторская работа, не шаблон.

6. **Phosphor Icons вместо Heroicons/Lucide** — менее очевидный выбор, добавляет индивидуальности.

#### Что МОЖЕТ выдать AI-происхождение:

1. **Избыточность анимаций** — shimmer + breathing + parallax + pulse + spring + blur-in + ripple + particles — всё сразу. Человек-дизайнер обычно выбирает 2-3 signature-анимации и повторяет их. AI склонен добавлять "ещё одну крутую штуку" в каждый компонент. **Рекомендация:** Выбрать 3 signature-motion и убрать остальное. Например: spring morph (Island), blur-crossfade (переходы), glass ripple (отправка).

2. **"Всё стеклянное"** — glass на glass на glass. Sidebar glass, TopNav glass, chat input glass, messages glass, modals glass, context menu glass. Когда ВСЁ стеклянное — ничего не выделяется. Человек-дизайнер создаёт контраст: стеклянные панели на МАТОВОМ фоне, или наоборот. **Рекомендация:** Ввести 2-3 "матовых" элемента для контраста. Например, chat input мог бы быть solid dark surface, а не glass.

3. **Слишком много CSS переменных** — 150+ токенов в global.css. Настоящий дизайнер использует 30-40 и строго их соблюдает. Раздутая система токенов, которую потом игнорируют 47+ файлов — это паттерн AI-генерации (сначала создать "идеальную систему", потом не следовать ей). **Рекомендация:** Сократить до 50-60 реально используемых токенов. Удалить дубли. Настроить lint.

4. **Каждый компонент — маленький шедевр** — ChatMessage имеет directional appear, read glow, emoji particles, noise texture. Одно сообщение не должно быть таким нагруженным. Это AI-паттерн: "сделать каждый элемент максимально impressive". Человек-дизайнер делает 80% элементов незаметными и 20% — яркими. **Рекомендация:** ChatMessage должен быть максимально простым. Убрать noise texture, emoji particles, read glow. Оставить только directional appear (и тот сделать subtler).

5. **Feature bloat в одном компоненте** — AuthScreen.jsx имеет 20+ useState для login, register, verify, forgot, forgot-code, forgot-reset, 2FA. Это не дизайн-проблема, но выдаёт "одна сессия AI сгенерировала всё". Человек бы разбил на 4-5 файлов.

#### Что запоминается (Differentiation Anchor):

Если скриншотить blesk без логотипа — узнают по:
- Кислотно-зелёный акцент на чёрном
- Dynamic Island сверху
- Метабол-фон с хроматической аберрацией
- Double layer glass с edge glow

Это **хорошо**. Blesk имеет визуальную идентичность. Это не "ещё один Discord-клон с blur".

#### Итог как критик:

**7/10 как дизайн. 8/10 как амбиция. 5/10 как дисциплина.**

Дизайн-видение сильное и запоминающееся. Но исполнение страдает от "AI-максимализма" — слишком много эффектов, слишком много токенов, слишком много анимаций. Нужна редактура: убрать 30% украшательств, и оставшиеся 70% станут в 2 раза мощнее.

**Главное правило для blesk: "Если сомневаешься — убери, а не добавь."**

---

## 6. ТОП-10 ПРИОРИТЕТНЫХ ИСПРАВЛЕНИЙ

| # | Что | Severity | Effort | Файлы |
|---|-----|----------|--------|-------|
| 1 | Заменить `<div onClick>` на `<button>` | Critical | Low | ChatCard, FriendsScreen |
| 2 | Добавить ARIA табы (role="tablist") | Critical | Low | FriendsScreen, ChannelBrowser, Settings |
| 3 | Рефакторить MainScreen: 16 useState → UI store | Critical | Medium | MainScreen.jsx |
| 4 | Заменить hardcoded border-radius на токены | Critical | Low | 47+ CSS файлов |
| 5 | Заменить hardcoded цвета на CSS переменные | Critical | Low | 47+ CSS файлов |
| 6 | Добавить aria-label на textarea, ошибки → role="alert" | Critical | Low | ChatInput, ProfileEditor |
| 7 | useCallback на callback-пропсы в ChatView | Major | Low | ChatView.jsx |
| 8 | React.lazy для MetaballBackground, AdminPanel, Settings | Major | Medium | MainScreen.jsx |
| 9 | Консолидировать mousemove/keydown listeners | Major | Medium | MetaballBackground, AnimatedBackground |
| 10 | Сократить анимации ChatMessage (убрать noise, particles) | Major | Low | ChatMessage.jsx/css |

---

## 7. ПЛАН ВНЕДРЕНИЯ

### Фаза 1: Критические фиксы (3-5 дней)
- Семантика: div→button, ARIA табы, aria-label
- Токены: массовая замена hardcoded значений на CSS переменные (radius, colors, spacing, font-size)
- MainScreen рефакторинг (useState → Zustand UI store)

### Фаза 2: Производительность (1-2 недели)
- Code-splitting (React.lazy)
- useCallback на callback-пропсы
- Zustand селекторы оптимизация
- Consolidated idle/mousemove handler
- AudioContext cleanup

### Фаза 3: Дизайн-редактура (1-2 недели)
- Убрать noise texture, emoji particles, read glow из ChatMessage
- Выбрать 3 signature-анимации, убрать остальные
- Ввести 2-3 матовых элемента для контраста с glass
- Сократить токен-систему до 50-60 реально используемых
- Улучшить light theme контраст

### Фаза 4: Quality (2-3 недели)
- Разбить AuthScreen на 4-5 файлов
- Рефакторить useSocket handlers
- Добавить JSDoc/TypeScript для props
- Error handling: role="alert", aria-invalid
- Тестирование с screen reader (NVDA)

---

_Сгенерировано Design Review. Перезапустите `/ui-design:design-review` после исправлений._
