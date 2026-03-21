# Auth Screen Redesign — Gravity Cards + Organic Split

## Обзор

Полная переработка экрана авторизации blesk. Текущий дизайн — скучная карточка по центру без характера. Новый — Gravity Cards (парящие наклонённые карточки) + Organic Split (живая граница между metaball-миром и формой).

## Решения (утверждённые)

| Вопрос | Решение |
|--------|---------|
| Layout | Organic Split — левая часть metaball, правая карточки |
| Граница | Blob Edge — SVG path, анимированный GSAP |
| Форма | Gravity Cards — парящие карточки с наклоном |
| Каждый элемент | Переделан — иконки, floating labels, liquid border, specular highlight |
| Настроение | Живая энергия — всё реагирует на мышь, пульсирует |

## Архитектура компонентов

### Файловая структура (новые/изменённые)

```
client/src/components/auth/
├── AuthScreen.jsx          # ПЕРЕПИСАТЬ — новая структура
├── AuthScreen.css          # ПЕРЕПИСАТЬ — новые стили
├── GravityCard.jsx         # НОВЫЙ — переиспользуемая парящая карточка
├── GravityCard.css         # НОВЫЙ — стили карточки
├── OrganicDivider.jsx      # НОВЫЙ — SVG divider с GSAP морфом
├── PasswordCard.jsx        # НОВЫЙ — карточка пароля с двумя фазами
└── StrengthDots.jsx        # НОВЫЙ — индикатор силы пароля (точки)
```

### Компонентная схема

```
<AuthScreen>
  ├── <MetaballBackground />          # Уже есть (Three.js)
  ├── <div class="auth-split">
  │   ├── <div class="auth-left">
  │   │   ├── metaball blobs (Three.js — из MetaballBackground)
  │   │   ├── <img src="blesk.png" />  # Логотип
  │   │   ├── tagline: "твой блеск. твои правила."
  │   │   └── version badge: "v0.5.7-beta"
  │   │
  │   ├── <OrganicDivider />           # SVG path + GSAP morph
  │   │
  │   └── <div class="auth-right">
  │       ├── <AnimatePresence mode="wait">
  │       │   ├── [mode=login]
  │       │   │   ├── <GravityCard tilt={-1.5}>  # "Кто ты?" — username
  │       │   │   └── <GravityCard tilt={1}>     # "Докажи" — password
  │       │   │
  │       │   ├── [mode=register]
  │       │   │   ├── <GravityCard tilt={-1.5}>  # "Придумай имя" — username
  │       │   │   ├── <GravityCard tilt={1}>     # "Куда писать?" — email
  │       │   │   └── <PasswordCard tilt={-0.7}> # "Придумай пароль" → "Подтверди"
  │       │   │
  │       │   ├── [mode=forgot]
  │       │   │   └── <GravityCard tilt={0}>     # "Вспомним?" — email
  │       │   │
  │       │   └── [mode=verify]
  │       │       └── <GravityCard tilt={0}>     # "Проверь почту" — 6 digits
  │       │
  │       ├── <ActionButton />                    # "Войти" / "Создать аккаунт"
  │       └── <AuthFooter />                      # Ссылки переключения
  │       </AnimatePresence>
  │   </div>
  └── </div>
</AuthScreen>
```

## Компоненты (детально)

### 1. GravityCard

Переиспользуемая парящая glass-карточка.

**Props:**
- `tilt: number` — угол наклона в градусах (напр. -1.5, 1, -0.7)
- `icon: ReactNode` — Lucide иконка
- `title: string` — заголовок (Bricolage Grotesque, 19px, 700)
- `subtitle: string` — подзаголовок (Manrope, 11px, rgba white 0.25)
- `error: string | null` — сообщение об ошибке
- `focused: boolean` — состояние фокуса
- `children: ReactNode` — инпут внутри

**Визуал:**
- Double Layer Glass: outer bg rgba(255,255,255,0.025), inner layer inset 6px с rgba(255,255,255,0.015)
- Border: 1px solid rgba(255,255,255,0.06)
- Edge glow: pseudo ::after top gradient rgba(255,255,255,0.08)
- Shadow: 0 8px 40px rgba(0,0,0,0.3), inset 0 1px rgba(255,255,255,0.04)
- Border-radius: 22px outer, 17px inner
- Padding: 22px 26px
- Width: 360px

**Иконка:**
- 36x36px, border-radius 11px
- Background: rgba(200,255,0,0.06), border rgba(200,255,0,0.12)
- Shadow: 0 0 12px rgba(200,255,0,0.04)
- Stroke: #c8ff00

**Состояния:**
- Default: тихо парит с заданным tilt
- Hover: tilt уменьшается к 0°, scale 1.015, translateY -2px, усиленная тень
- Focused: scale 1.02, tilt→0°, z-index 10, остальные карточки scale 0.98 + opacity 0.7
- Error: border rgba(239,68,68,0.2), edge glow красный, иконка красная, shake animation

**Анимация:**
- Framer Motion: `initial={{ opacity: 0, y: 30, rotate: 0 }}`, `animate={{ opacity: 1, y: 0, rotate: tilt }}`
- Spring: stiffness 120, damping 14
- Stagger: delay index * 0.12s
- Parallax tilt на hover: useMotionValue для rotateX/Y ±4°, useSpring damping 25

### 2. PasswordCard

Специальная карточка для пароля с двумя фазами.

**Фаза 1: "Придумай пароль"**
- GravityCard с полем пароля + StrengthDots
- Eye toggle для показа/скрытия
- При вводе пароля и Tab/Enter → переход к фазе 2

**Фаза 2: "Подтверди пароль"**
- Та же карточка, анимация перехода через Framer Motion layoutId
- Заголовок морфится: "Придумай пароль" → "Подтверди пароль"
- Иконка меняется: Lock → ShieldCheck
- Поле очищается, фокус автоматически

**Результат:**
- Пароли совпали: иконка → зелёная галочка, border зелёный glow, subtitle "Пароли совпадают"
- Не совпали: shake, error msg "Пароли не совпадают", вернуть к фазе 1

### 3. StrengthDots

Индикатор силы пароля — 5 точек вместо полоски.

**Визуал:**
- 5 точек, 8x8px, border-radius 50%, gap 6px
- Неактивные: rgba(255,255,255,0.06)
- Активные по силе:
  - 1-2 точки (слабый): #ef4444 + glow
  - 3 точки (средний): #eab308 + glow
  - 4-5 точек (сильный): #c8ff00 + glow
- Текст-лейбл справа: "Слабый" / "Средний" / "Сильный"

### 4. OrganicDivider

SVG линия-граница между левой и правой частью.

**Визуал:**
- SVG path — волнистая линия, stroke rgba(200,255,0,0.06)
- Левая часть заполнена #08060f (чтобы закрыть фон)
- Width: 80px, height: 100%

**Анимация:**
- GSAP timeline: morph между 3 вариантами path
- Каждый морф 4 секунды, ease: "power1.inOut"
- Timeline repeat: -1 (бесконечно)
- Mouse proximity: при приближении к divider — волнистость увеличивается

### 5. AuthScreen (переписанный)

**Режимы (mode):**
- `login` — 2 карточки (username, password)
- `register` — 3 карточки (username, email, PasswordCard)
- `forgot` — 1 карточка (email)
- `verify` — 1 карточка (6-digit code)

**Переключение режимов:**
- AnimatePresence mode="wait"
- Exit: cards slide up (y: -20) + fade (opacity: 0), duration 0.3s
- Enter: new cards slide from bottom (y: 30→0) + fade, stagger 0.12s

**Layout:**
- auth-split: display flex, height 100vh (минус titlebar)
- auth-left: flex 0 0 40%, center content
- auth-right: flex 1, center content, padding 24px 48px, gap 16px

**Логотип (левая часть):**
- blesk.png (80x80px)
- drop-shadow: 0 0 30px rgba(200,255,0,0.2)
- Tagline: "твой блеск. твои правила." — 10px, uppercase, letter-spacing 3.5px
- Version badge: pill с border, font-size 9px

**Footer:**
- Login mode: "Забыли пароль? | Создать аккаунт" — через разделитель
- Register mode: "Уже есть аккаунт? Войти"
- Forgot mode: "Вспомнили? Войти"
- Font-size: 12px, color rgba(255,255,255,0.2), hover → rgba(200,255,0,0.5)

**Кнопка действия:**
- Background: #c8ff00
- Border-radius: 16px
- Padding: 15px
- Font: Manrope, 15px, 700, color #08060f
- Shadow: 0 4px 28px rgba(200,255,0,0.2)
- Specular highlight: ::before, top gradient rgba(255,255,255,0.45)
- Hover: translateY -1px, shadow усиливается
- Active: scale 0.98
- Loading: background 65% opacity, spinner (14px, border animation)

**Инпуты:**
- Background: rgba(255,255,255,0.035)
- Border: 1px solid rgba(255,255,255,0.05)
- Border-radius: 14px
- Padding: 14px 18px
- Font: Manrope, 15px
- Placeholder: rgba(255,255,255,0.15)
- Focus: border rgba(200,255,0,0.25), bg rgba(200,255,0,0.02), shadow 0 0 0 3px rgba(200,255,0,0.04)

**Email verification (6 digits):**
- 6 ячеек, flex с gap 8px
- Каждая: border-radius 12px, font Bricolage Grotesque, 20px, 700
- Заполненные: bg rgba(200,255,0,0.04), border rgba(200,255,0,0.15), color #c8ff00
- Пустые: bg rgba(255,255,255,0.03), border rgba(255,255,255,0.06), color rgba(255,255,255,0.15)
- Таймер повторной отправки: "Отправить повторно через 2:47"

## Анимации (полный список)

| # | Название | Технология | Параметры |
|---|----------|-----------|-----------|
| 1 | Card Entrance | Framer Motion | stagger 0.12s, y: 30→0, opacity 0→1, rotate 0→tilt. Spring stiffness 120, damping 14 |
| 2 | Parallax Tilt | Framer Motion useMotionValue + useSpring | rotateX/Y ±4° от позиции мыши. damping 25. Reset on mouseleave |
| 3 | Focus Pull | Framer Motion animate | Focused: scale 1.02, rotate→0, zIndex 10. Others: scale 0.98, opacity 0.7. Duration 0.4s |
| 4 | Organic Divider Morph | GSAP morphSVG | 3 path варианта, каждый 4s, ease "power1.inOut", repeat -1 |
| 5 | Input Glow | CSS transition + Framer Motion | border-color, box-shadow, background transition 0.3s |
| 6 | Error Shake | Framer Motion | x: [0,-8,8,-4,4,0], duration 0.5s. Border flash red. Icon color transition |
| 7 | Login Success | GSAP timeline | Cards: scale 0.8, opacity 0. Divider: width expands. Metaballs: fill screen. Crossfade to MainScreen. Total ~1.2s |
| 8 | Mode Switch | Framer Motion AnimatePresence | Exit: y -20, opacity 0, 0.3s. Enter: y 30→0, opacity 0→1, stagger 0.12s |
| 9 | Button Ripple | GSAP | Radial gradient from click point. rgba(255,255,255,0.2). Duration 0.5s ease-out |
| 10 | Password Phase Transition | Framer Motion layoutId | Title morphs, icon changes, field clears. Spring transition. Duration ~0.5s |
| 11 | Strength Dots Fill | Framer Motion | Scale 0→1 + opacity. Stagger 0.05s. Color transition with glow |
| 12 | Metaball Background | Three.js (уже готов) | Raymarching shader, mouse tracking, ambient hue |

## Обработка ошибок

| Ошибка | Поведение |
|--------|-----------|
| Неверный логин/пароль | Обе карточки shake. Error msg под username карточкой. Иконки → красные. Border flash. |
| Пустое поле при submit | Пустая карточка shake. Focus pull на неё. Error "Заполни это поле" |
| Пароли не совпадают | PasswordCard shake. Возврат к фазе 1. Error "Пароли не совпадают" |
| Слабый пароль | StrengthDots красные (1-2). Subtitle: "Слишком простой". Блокирует submit |
| Username занят | Username карточка shake. Error "Имя уже занято". Иконка → красная |
| Email невалидный | Email карточка shake. Error "Проверь адрес" |
| Неверный код | Все 6 ячеек flash красным. Shake. Очистка полей |
| Сервер недоступен | Toast notification сверху. Retry через 5 секунд |

## Accessibility

- Все инпуты имеют aria-label
- Tab navigation между карточками (порядок: сверху вниз)
- Focus-visible: outline 1.5px solid rgba(200,255,0,0.4) с offset 2px
- Error messages связаны через aria-describedby
- prefers-reduced-motion: отключает parallax tilt, card entrance instant, divider morph отключён
- Keyboard: Enter на последнем поле = submit

## Адаптивность

Экран авторизации отображается только в Electron (десктоп, фиксированное окно).
Минимальный размер окна: 800x600px.

При маленьком окне (< 900px width):
- auth-left скрывается (display none)
- OrganicDivider скрывается
- auth-right занимает 100%, padding увеличивается
- Логотип переносится наверх auth-right

## Зависимости

Все уже установлены:
- `framer-motion` ^12.38.0 — card animations, AnimatePresence, layoutId
- `gsap` ^3.14.2 — divider morph, button ripple, success transition
- `three` ^0.183.2 — MetaballBackground (уже готов)
- `lucide-react` ^0.577.0 — иконки (User, Lock, Mail, ShieldCheck, Eye, EyeOff, AlertCircle, Check)

## Миграция

Текущий AuthScreen.jsx (874 строки) будет полностью переписан. Функциональность сохраняется:
- Login flow (JWT + refresh tokens)
- Registration flow (username + email + password)
- Email verification (6-digit code)
- Password recovery (3 steps)
- Error handling
- Password strength indicator

Бизнес-логика (API calls, token management, validation) извлекается без изменений. Меняется только UI layer.
