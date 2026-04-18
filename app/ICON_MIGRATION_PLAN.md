# План миграции иконок на Solar Icons

## Состояние

- **Установлено:** `solar_icons: ^0.0.4`
- **Удалено:** `phosphor_flutter` (не использовался — только добавлен в pubspec, imports не было)
- **Других пакетов не найдено:** FontAwesome, Lucide, Heroicons, Iconsax — все отсутствуют
- **Текущие иконки:** только `Icons.*` из Material
- **Уникальных иконок:** ~105
- **Затрагиваемых файлов:** 20

## Распределение по весам (согласно blesk-системе)

| Вес | Использование | Кол-во уникальных |
|---|---|---|
| `SolarIconsOutline` | default idle state (90%) | ~92 |
| `SolarIconsBold` | active/pressed/play/hangup | ~9 |
| `SolarIconsBoldDuotone` | accent wow-моменты (лимит ≤5) | **1** |
| `SolarIconsBroken` | empty/error states | 1 (в empty ChannelFeed) |

### Использование BoldDuotone (wow — только 1 место сейчас)

| # | Контекст | Иконка | Файл |
|---|---|---|---|
| 1 | Read receipts ✓✓ accent (прочитано) | `checkRead` (или сборка из 2 check) | chat_bubble_parts.dart |

Резерв для будущих wow-моментов (не реализованы в коде, добавим по мере):
- Saved Messages звезда (C1 — пост-релиз) — `SolarIconsBoldDuotone.star`
- Новые сообщения badge — `SolarIconsBoldDuotone.bellBing`
- Верификация — `SolarIconsBoldDuotone.verifiedCheck` (если есть)
- Один wow на onboarding

## Маппинг 105 иконок

### Навигация (16)
| Material | Solar | Вес |
|---|---|---|
| `add` | `addCircle` | Outline |
| `close` | `closeCircle` (overlay) / `close` (simple) | Outline |
| `remove` | `minusSquare` / `minusCircle` | Outline |
| `arrow_back_ios_new_rounded` | `altArrowLeft` | Outline |
| `arrow_forward_ios_rounded` | `altArrowRight` | Outline |
| `arrow_downward_rounded` | `arrowDown` | Outline |
| `arrow_upward` | `arrowUp` | Outline |
| `chevron_right` | `altArrowRight` | Outline |
| `keyboard_arrow_down(_rounded)` | `altArrowDown` | Outline |
| `keyboard_arrow_up(_rounded)` | `altArrowUp` | Outline |
| `keyboard_return_rounded` | `loginDoubleLeft` (или backspace) | Outline |
| `keyboard_capslock` | `caseSensitive` (или `textBold`) | Outline |
| `crop_square_outlined` | `maximizeSquare` | Outline |
| `close_fullscreen` | `minimizeSquare` | Outline |
| `more_horiz` | `menuDots` | Outline |
| `grid_view_outlined` | `widgetSquare` | Outline |

### Чат / сообщения (19)
| Material | Solar | Вес |
|---|---|---|
| `send` | `plain` | **Bold** (active send) |
| `attach_file` | `paperclip` | Outline |
| `mood_outlined` | `smileCircle` | Outline |
| `chat_bubble_outline` / `_rounded` | `chatRound` | Outline |
| `reply_outlined` / `reply_rounded` | `undoLeft` (или `replyLeft`) | Outline |
| `forward_outlined` | `forward` (или `undoRight`) | Outline |
| `edit(_outlined)` | `pen` | Outline |
| `delete_outline` | `trashBinTrash` | Outline |
| `copy_outlined` / `copy_rounded` | `copy` | Outline |
| `push_pin(_outlined)` | `pin` | Outline |
| `search` / `search_rounded` | `magnifer` | Outline |
| `search_off_rounded` | `magnifer` + красный tint (нет `X` варианта) | Outline |
| `schedule_outlined` | `clockCircle` | Outline |
| `check` | `check` | Outline |
| `check_circle_outline_rounded` | `checkCircle` | Outline |
| `check_circle_rounded` | `checkCircle` | Bold |
| `done` | `check` | Outline |
| `done_all` | `checkRead` | **BoldDuotone** (wow #1) |
| `error_outline` | `dangerCircle` | Outline |

### Медиа (14)
| Material | Solar | Вес |
|---|---|---|
| `play_arrow_rounded` | `play` | **Bold** |
| `pause` / `pause_rounded` | `pause` | **Bold** |
| `camera_alt_outlined` | `camera` | Outline |
| `videocam` / `videocam_outlined` | `videocamera` | Outline (idle) / Bold (active) |
| `videocam_off` | `videocameraRecord` (или `videocamera` + mute overlay) | Outline |
| `mic` | `microphone` | **Bold** |
| `mic_none` | `microphone` | Outline |
| `mic_off` | `microphoneMute` | Outline |
| `image_outlined` / `photo_outlined` | `gallery` | Outline |
| `audiotrack_outlined` / `music_note_outlined` | `musicNote` | Outline |
| `file_download_outlined` | `downloadMinimalistic` | Outline |
| `cloud_upload_outlined` | `uploadMinimalistic` | Outline |
| `volume_off_outlined` | `volumeCross` (или `volumeMute`) | Outline |
| `rss_feed_rounded` | `podcast` | Outline |

### Звонки (3)
| Material | Solar | Вес |
|---|---|---|
| `call` | `phone` | Outline |
| `call_end` | `phone` + red color + rotation (нет hangup в Solar 0.0.4) | **Bold** |
| `phone_outlined` | `phone` | Outline |
| `screen_share_outlined` | `monitorCamera` (или `monitor`) | Outline |
| `stop_screen_share` | `monitor` (с tint) | Outline |

### Профиль / контакты (9)
| Material | Solar | Вес |
|---|---|---|
| `person_outline` | `user` | Outline |
| `person_add_alt_outlined` | `userPlus` | Outline |
| `person_remove_outlined` | `userMinus` | Outline |
| `block_outlined` | `forbiddenCircle` | Outline |
| `group_outlined` / `people_outline` | `usersGroupRounded` | Outline |
| `workspace_premium_outlined` | `crown` | Outline |
| `remove_moderator_outlined` | `shieldCross` (или `shieldMinus`) | Outline |
| `mail_outline` | `letter` | Outline |
| `face_outlined` | `smileCircle` | Outline |

### Settings / система (15)
| Material | Solar | Вес |
|---|---|---|
| `settings_outlined` | `settings` | Outline |
| `notifications_active_outlined` | `bellBing` | Outline |
| `notifications_none_outlined` | `bell` | Outline |
| `notifications_off_outlined` | `bellOff` | Outline |
| `lock_outline` | `lock` | Outline |
| `shield_outlined` | `shield` | Outline |
| `visibility_outlined` | `eye` | Outline |
| `visibility_off_outlined` | `eyeClosed` | Outline |
| `info_outline` | `infoCircle` | Outline |
| `help_outline` | `questionCircle` | Outline |
| `language_outlined` | `global` | Outline |
| `palette_outlined` | `paletteRound` | Outline |
| `storage_outlined` | `database` | Outline |
| `devices_outlined` | `devicesSmart` (или `monitor`) | Outline |
| `share_outlined` | `shareCircle` (или `shareEnd`) | Outline |

### Устройства / состояния (8)
| Material | Solar | Вес |
|---|---|---|
| `desktop_windows` | `monitor` | Outline |
| `laptop_mac` | `laptop` | Outline |
| `phone_android` | `smartphone` | Outline |
| `wifi_off` | `wiFiRouterMinimalisticOff` (если есть) / `wiFiRouterMinimalistic` | Outline |
| `favorite` | `heart` | **Bold** |
| `favorite_border` | `heart` | Outline |
| `unfold_less` | `altArrowUp` | Outline |
| `unfold_more` | `altArrowDown` | Outline |

### Прочее (4)
| Material | Solar | Вес |
|---|---|---|
| `archive_outlined` | `archive` | Outline |
| `bolt_outlined` | `bolt` | Outline |
| `campaign_outlined` | `megaphone` (или `podcast`) | Outline |
| `link_rounded` | `link` | Outline |
| `public` | `global` | Outline |
| `poll_outlined` | `chartSquare` (или `pieChart`) | Outline |
| `apple` | остаётся Material Icons (бренд-логотип, нет аналога) | — |
| `people_outline` (empty channel feed) | `usersGroupRounded` → в broken варианте нет, оставлю Outline; **вместо `rss_feed_rounded`** в empty state → `SolarIconsBroken.podcast` (единственное empty) | Broken |

## Файлы для правки (20)

| # | Файл | Приблизит. кол-во иконок |
|---|---|---|
| 1 | `lib/screens/shared/widgets.dart` | 4 |
| 2 | `lib/screens/shared/slide_over.dart` | 1 |
| 3 | `lib/screens/onboarding_screen.dart` | 8 |
| 4 | `lib/screens/login_screen.dart` | 4 |
| 5 | `lib/screens/forgot_password_screen.dart` | 3 |
| 6 | `lib/screens/main_screen.dart` | 35+ |
| 7 | `lib/screens/features/chat_messages.dart` | 20+ |
| 8 | `lib/screens/features/chat_bubble_parts.dart` | 15+ |
| 9 | `lib/screens/features/chat_search_bar.dart` | 4 |
| 10 | `lib/screens/features/input_bar.dart` | 8 |
| 11 | `lib/screens/features/emoji_picker.dart` | 2 |
| 12 | `lib/screens/features/media_viewer.dart` | 15+ |
| 13 | `lib/screens/features/global_search.dart` | 6 |
| 14 | `lib/screens/features/create_flows.dart` | 8 |
| 15 | `lib/screens/features/channel_feed.dart` | 12 |
| 16 | `lib/screens/features/members_panel.dart` | 10+ |
| 17 | `lib/screens/features/profile_view.dart` | 5+ |
| 18 | `lib/screens/features/settings_screen.dart` | 20+ |
| 19 | `lib/screens/features/call_ui.dart` | 8 |
| 20 | `lib/screens/features/drop_overlay.dart` | 2 |

## Риски и нюансы Solar 0.0.4

1. **Некоторые имена могут отсутствовать** — Solar быстро развивается. При замене буду проверять compile; если имя не найдено, подберу ближайшее и отмечу.
2. **`checkRead` (двойная галочка accent)** — если нет в Solar, соберу из двух `SolarIconsBold.check` с overlap через Stack.
3. **`phoneHangup`** — нет, используем `phone` с красным цветом + rotation 135° (стандартная практика).
4. **`crop_square_outlined` / window maximize** — если `maximizeSquare` нет, возможно только `maximize` или `squareMinimalistic`. Уточню при замене.
5. **Brand icons** — `Icons.apple` оставим как есть (логотипы Solar не покрывает).

## Порядок коммитов

1. `refactor(icons): add solar_icons, remove phosphor_flutter` — уже сделано (pubspec)
2. `refactor(icons): migrate shared widgets to solar` — 2 файла
3. `refactor(icons): migrate auth screens to solar` — 3 файла (onboarding/login/forgot)
4. `refactor(icons): migrate chat bubbles and input to solar` — 5 файлов
5. `refactor(icons): migrate overlays (media, search, flows) to solar` — 6 файлов
6. `refactor(icons): migrate main shell and settings to solar` — 4 файла
7. `refactor(icons): finalize — QA pass` — исправления после визуальной проверки

## Действие

**ЖДУ подтверждения**. После твоего «ок» начну заменять по файлам (сначала shared/widgets, потом группами).
