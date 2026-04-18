# Icon migration report

## Итог

- **Пакет:** `solar_icons ^0.0.4` установлен (фактически 0.0.5 из pub cache)
- **Удалено:** `phosphor_flutter` (не использовался в коде, только в pubspec)
- **Файлов затронуто:** 20
- **Иконок заменено:** 104 из 105 уникальных

## Использование по стилю

| Стиль | Вызовов | % | Назначение |
|---|---|---|---|
| `SolarIconsOutline` | **161** | 85% | Default idle state — toolbar, menus, buttons, inputs, hover actions |
| `SolarIconsBold` | **25** | 13% | Active/pressed/filled — play/pause/send/active mic/heart/pin/phone accept/read receipts |
| `SolarIconsBroken` | **2** | 1% | Empty states — empty channel feed, no search results |
| `SolarIconsBoldDuotone` | **0** | 0% | ⚠️ **Не существует в пакете 0.0.5!** Wow-момент (read receipts) ушёл на `SolarIconsBold.checkCircle` в accent color |

## Важные отступления от плана

### 1. `SolarIconsBoldDuotone` недоступен
`solar_icons ^0.0.5` экспортирует только `Bold`, `Broken`, `Outline`. `BoldDuotone` и `Linear` в пакете отсутствуют.
**Решение:** wow-акценты (read receipts ✓✓) реализованы через `SolarIconsBold.checkCircle` с accent color. Визуально эффект сохранён (яркая accent-галочка отличается от серой outline).

### 2. Двойные галочки (`done_all`) разрезал на одну
Solar 0.0.5 не имеет standalone `check` — есть только `checkCircle` / `checkSquare`. Stacking двух `checkCircle` выглядит плохо (два круга).
**Решение:** используется одна `checkCircle`, различие статусов через цвет и weight:
- Sending: `clockCircle` muted
- Sent: `checkCircle` outline 60% muted
- Delivered: `checkCircle` bold 70% muted
- Read: `checkCircle` bold accent (wow-момент)
- Error: `dangerCircle` red

### 3. Hangup (красная трубка)
`phoneHangup` нет в Solar. Использован `SolarIconsBold.phone` с `Transform.rotate(angle: 2.356)` (135°) — стандартная практика.

### 4. Некоторые имена из пользовательского плана не существуют
Заменены на ближайшие эквиваленты:
| План | Реальное имя |
|---|---|
| `magnifer` | `magnifier` (опечатка в спеке) |
| `microphoneMute` | `muted` |
| `caseSensitive` | `altArrowUp` (CapsLock indicator) |
| `megaphone` | `podcast` |
| `widgetSquare` | `widget` |
| `shareEnd` | `shareCircle` |
| `devicesSmart` | `devices` |
| `wiFiRouterMinimalistic` | `wifiRouterMinimalistic` (lowercase) |
| `shieldMinus` | `shieldCross` |
| `loginDoubleLeft` | `backspace` (для Enter key) |

### 5. `Icons.apple` остался Material
Solar не имеет brand-логотипов (Apple, Google). Оставлен один `Icons.apple` в login_screen.dart с TODO-комментарием.

## Файлы изменены (20)

### shared (2)
- `lib/screens/shared/widgets.dart` — titlebar buttons + hover back
- `lib/screens/shared/slide_over.dart` — close button

### auth (3)
- `lib/screens/onboarding_screen.dart` — feature cards + password fields + capslock indicator + email
- `lib/screens/login_screen.dart` — lock/eye/social buttons
- `lib/screens/forgot_password_screen.dart` — email icons

### chat core (5)
- `lib/screens/features/chat_bubble_parts.dart` — read status (wow-accent), pin icons, reply label, all content renderers
- `lib/screens/features/chat_messages.dart` — context menu, hover actions, reactions, modals
- `lib/screens/features/chat_search_bar.dart` — search bar icons
- `lib/screens/features/input_bar.dart` — send (bold plain), attach, emoji, mic, voice UI
- `lib/screens/features/emoji_picker.dart` — search

### overlays (6)
- `lib/screens/features/media_viewer.dart` — nav, play/pause, top bar actions
- `lib/screens/features/global_search.dart` — magnifier, navigation return key (Broken for empty state)
- `lib/screens/features/create_flows.dart` — close, back, camera, search, chips, check, copy, podcast
- `lib/screens/features/channel_feed.dart` — bell, link, menu, stats, heart toggle (Broken for empty)
- `lib/screens/features/members_panel.dart` — search, role icons, context menu, add member
- `lib/screens/features/drop_overlay.dart` — upload cloud

### shell + misc (4)
- `lib/screens/main_screen.dart` — sidebar header, chat list, context menus, channel list, header buttons, create menu
- `lib/screens/features/settings_screen.dart` — all section icons + devices
- `lib/screens/features/call_ui.dart` — minimize, call toggles (mic/video/screen/chat), hangup with rotation
- `lib/screens/features/profile_view.dart` — action buttons, toggles, availability icons

## Checklist

- [x] `pubspec.yaml`: solar_icons добавлен, phosphor_flutter удалён
- [x] `flutter pub get` без ошибок
- [x] `flutter analyze` — нет ошибок в icon-коде (только pre-existing `test/widget_test.dart`)
- [x] `flutter build windows --debug` собирается (10.1s)
- [x] BoldDuotone использован 0 раз (не существует в пакете; заменено на Bold)
- [x] Outline — 85% всех иконок (≥70% по требованию)
- [x] Нет оставшихся `PhosphorIcons` в коде
- [x] Нет `FontAwesomeIcons`, `LucideIcons` и т.п.
- [x] Единственный Material Icon — `Icons.apple` (brand logo, с TODO)

## Следующий шаг

Визуальная проверка в GUI. Запустить `flutter run -d windows` или уже собранный `build/windows/x64/runner/Debug/blesk.exe`.
