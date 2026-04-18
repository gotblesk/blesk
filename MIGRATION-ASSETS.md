# blesk — Инвентаризация ассетов для миграции Electron → Flutter

> Создано: 2026-04-08 | Фаза 0.3-0.4

---

## 1. API-эндпоинты бэкенда (контракт клиент-сервер)

Базовый URL: `https://blesk.fun` (production) / `http://localhost:3000` (dev)

### AUTH — `/api/auth/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | /csrf | yes | CSRF-токен |
| POST | /register | no | Регистрация (username, email, password) |
| POST | /verify-email | yes | Подтверждение email (код) |
| POST | /resend-code | yes | Повторная отправка кода верификации |
| POST | /login | no | Авторизация (email/username + password) |
| POST | /refresh | no | Обновление access token (refresh token в body) |
| POST | /logout | no | Выход (инвалидация refresh token) |
| GET | /me | yes | Текущий пользователь |
| POST | /forgot-password | no | Запрос сброса пароля |
| POST | /verify-reset-code | no | Проверка кода сброса |
| POST | /reset-password | no | Установка нового пароля |
| POST | /change-password/request | yes | Запрос смены пароля (отправка кода) |
| POST | /change-password/confirm | yes | Подтверждение смены пароля |
| POST | /2fa/setup | yes | Настройка 2FA (генерация секрета) |
| POST | /2fa/verify | yes | Подтверждение 2FA (активация) |
| POST | /2fa/disable | yes | Отключение 2FA |
| POST | /2fa/login | no | Вход с 2FA-кодом |
| POST | /keys | yes | Загрузка публичного ключа E2E |
| DELETE | /account | yes | Удаление аккаунта |
| GET | /sessions | yes | Список активных сессий |
| DELETE | /sessions/:id | yes | Завершение конкретной сессии |

### USERS — `/api/users/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | /me/export | yes | Экспорт данных пользователя (GDPR) |
| GET | /search | yes | Поиск пользователей (?q=) |
| GET | /blocked | yes | Список заблокированных |
| GET | /:id | yes | Профиль пользователя |
| POST | /me/avatar | yes | Загрузка аватара (multipart) |
| PUT | /me | yes | Обновление профиля (username, bio, status, settings) |
| POST | /:id/block | yes | Заблокировать пользователя |
| DELETE | /:id/block | yes | Разблокировать пользователя |
| DELETE | /me | yes | Удаление аккаунта |

### CHATS — `/api/chats/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | /search | yes | Поиск по сообщениям (?q=) |
| GET | / | yes | Список чатов текущего пользователя |
| GET | /:id/messages | yes | Сообщения чата (пагинация: ?before=&limit=) |
| POST | / | yes+verified | Создание чата (personal/group) |
| GET | /:id/members | yes | Участники группового чата |
| POST | /:id/members | yes | Добавление участника |
| DELETE | /:id/members/:userId | yes | Удаление участника |
| PATCH | /:id | yes | Обновление чата (name, avatar) |
| POST | /:id/messages/:msgId/pin | yes | Закрепить/открепить сообщение |
| POST | /:id/read | yes | Отметить чат как прочитанный |
| PUT | /:id/mute | yes | Замьютить чат |
| PUT | /:id/unmute | yes | Размьютить чат |

### UPLOAD — `/api/chats/` (тот же префикс)

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| POST | /channels/:channelId/upload | yes | Загрузка файла в канал |
| POST | /:chatId/upload | yes | Загрузка файла в чат (multipart) |
| GET | /attachments/:filename | yes | Скачивание вложения |
| GET | /thumbs/:filename | yes | Скачивание превью |

### FRIENDS — `/api/friends/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| POST | /request | yes+verified | Отправить запрос в друзья |
| GET | /requests/pending | yes | Входящие запросы |
| GET | /requests/sent | yes | Исходящие запросы |
| DELETE | /requests/:id | yes | Отменить запрос |
| POST | /requests/:id/accept | yes | Принять запрос |
| POST | /requests/:id/decline | yes | Отклонить запрос |
| GET | / | yes | Список друзей |
| DELETE | /:friendId | yes | Удалить из друзей |
| POST | /:userId/block | yes | Заблокировать |
| DELETE | /:userId/block | yes | Разблокировать |

### CHANNELS — `/api/channels/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | /my | yes | Мои каналы (подписки + владение) |
| GET | / | yes | Обзор каналов (?category=&sort=&search=) |
| GET | /:id | yes | Детали канала |
| POST | / | yes+verified | Создание канала |
| PATCH | /:id | yes | Обновление канала |
| POST | /:id/avatar | yes | Загрузка аватара канала |
| POST | /:id/cover | yes | Загрузка обложки канала |
| POST | /:id/subscribe | yes | Подписаться |
| GET | /:id/subscribers | yes | Список подписчиков |
| PATCH | /:id/mute | yes | Замьютить канал |
| DELETE | /:id/subscribe | yes | Отписаться |
| GET | /:id/posts | yes | Посты канала (пагинация) |
| POST | /:id/posts | yes | Создание поста |
| DELETE | /:id | yes | Удаление канала |

### VOICE — `/api/voice/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | /rooms | yes | Список голосовых комнат |
| POST | /rooms | yes | Создание комнаты |
| POST | /rooms/:id/invite | yes | Приглашение в комнату |
| DELETE | /rooms/:id/kick/:userId | yes | Кик из комнаты |
| DELETE | /rooms/:id | yes | Удаление комнаты |
| GET | /ice-servers | yes | ICE-серверы для WebRTC |

### NOTIFICATIONS — `/api/notifications/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | / | yes | Список уведомлений |
| GET | /unread-count | yes | Количество непрочитанных |
| POST | /read-all | yes | Отметить все как прочитанные |
| DELETE | /clear | yes | Очистить все |
| POST | /:id/read | yes | Отметить одно как прочитанное |
| DELETE | /:id | yes | Удалить уведомление |

### FEEDBACK — `/api/feedback/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| POST | / | yes | Отправить отзыв (type: bug/suggestion/question) |
| GET | / | yes | Мои отзывы |

### SHIELD (E2E ключи) — `/api/shield/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| POST | /bundle | yes | Загрузить ключевой бандл (identity + signed prekey + OPKs) |
| GET | /bundle/:userId | yes | Получить ключевой бандл пользователя |
| POST | /replenish | yes | Пополнить одноразовые prekeys |
| GET | /opk-count | yes | Количество оставшихся OPKs |
| GET | /key-log/:userId | yes | Лог смены ключей пользователя |
| POST | /key-log | yes | Записать событие смены ключа |

### GIF — `/api/gif/`

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | /search | yes | Поиск GIF (?q=&limit=) |
| GET | /featured | yes | Популярные GIF |

### ADMIN — `/api/internal/` (requireAdmin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | /stats | Статистика (пользователи, сообщения, онлайн) |
| GET | /users | Список пользователей (пагинация, поиск) |
| GET | /users/:id | Детали пользователя |
| PATCH | /users/:id | Редактирование пользователя |
| POST | /users/:id/ban | Бан |
| POST | /users/:id/unban | Разбан |
| POST | /users/:id/tags | Выдать тег |
| DELETE | /users/:id/tags/:tagId | Снять тег |
| GET | /tags | Все теги |
| POST | /tags | Создать тег |
| PATCH | /tags/:id | Обновить тег |
| DELETE | /tags/:id | Удалить тег |
| GET | /reports | Жалобы |
| PATCH | /reports/:id | Обновить статус жалобы |
| DELETE | /messages/:id | Удалить сообщение |
| GET | /logs | Логи действий |
| GET | /feedback | Все отзывы |
| PATCH | /feedback/:id | Обновить статус отзыва |
| GET | /channels | Все каналы |
| DELETE | /channels/:id | Удалить канал |
| POST | /broadcast-update | Отправить уведомление об обновлении всем |
| GET | /db/tables | Список таблиц БД |
| GET | /db/:table | Данные таблицы (пагинация) |
| GET | /server/config | Конфигурация сервера |

### Статические файлы

| Путь | Описание |
|------|----------|
| /uploads/avatars/:filename | Аватары пользователей и каналов |
| /uploads/attachments/:filename | Вложения (файлы, картинки) |
| /uploads/thumbs/:filename | Превью картинок (sharp) |

---

## 2. WebSocket-события (Socket.io)

### Чат

| Событие | Направление | Payload | Описание |
|---------|-------------|---------|----------|
| `message:send` | client → server | `{chatId, text, tempId, replyToId?, encrypted?, shieldHeader?}` | Отправка сообщения |
| `message:new` | server → client | `{message, isChannel?}` | Новое сообщение |
| `message:edit` | client → server | `{messageId, chatId, text, encrypted?}` | Редактирование |
| `message:delete` | client → server | `{messageId, chatId}` | Удаление |
| `message:read` | client → server | `{chatId, messageIds}` | Отметка прочитанным |
| `message:react` | client → server | `{messageId, emoji}` | Реакция на сообщение |
| `message:pin` | client → server | `{messageId, roomId}` | Закрепить/открепить |
| `message:forward` | client → server | `{messageId, targetChatId}` | Переслать |
| `message:reactions:get` | client → server | `{messageIds}` | Запрос реакций (батч) |
| `message:reactions:batch` | server → client | `[{messageId, reactions}]` | Ответ с реакциями |
| `message:error` | server → client | `{tempId?, error}` | Ошибка отправки |
| `typing:start` | client → server | `{chatId}` | Начал печатать |
| `typing:stop` | client → server | `{chatId}` | Перестал печатать |
| `channel:post` | client → server | `{channelId, text, tempId}` | Пост в канал |

### Пользователи

| Событие | Направление | Payload | Описание |
|---------|-------------|---------|----------|
| `user:online` | server → client | `{userId, status}` | Пользователь онлайн |
| `user:offline` | server → client | `{userId}` | Пользователь офлайн |
| `user:updated` | server → client | `{userId, avatar, updatedAt}` | Обновлён профиль |
| `user:statusChange` | server → client | `{userId, status}` | Смена статуса |
| `user:keyChanged` | server → client | `{userId}` | Смена E2E ключа |
| `auth:banned` | server → client | `{reason}` | Аккаунт забанен |

### Звонки (1-на-1)

| Событие | Направление | Payload | Описание |
|---------|-------------|---------|----------|
| `call:initiate` | client → server | `{chatId, video}` | Начать звонок |
| `call:accept` | client → server | `{chatId}` | Принять |
| `call:accept-confirmed` | server → client | `{chatId, userId, startedAt}` | Звонок начался |
| `call:decline` | client → server | `{chatId}` | Отклонить |
| `call:end` | client → server | `{chatId}` | Завершить |
| `call:cancel` | client → server | `{chatId}` | Отменить (до ответа) |
| `call:error` | server → client | `{chatId?, error}` | Ошибка |
| `call:busy` | server → client | `{chatId}` | Абонент занят |

### Голосовые комнаты (mediasoup)

| Событие | Направление | Payload | Описание |
|---------|-------------|---------|----------|
| `voice:join` | client → server | `{roomId}` | Войти в комнату |
| `voice:leave` | client → server | `{roomId}` | Покинуть комнату |
| `voice:createTransport` | client → server | `{roomId, direction}` | Создать транспорт (send/recv) |
| `voice:connectTransport` | client → server | `{roomId, transportId, dtlsParameters}` | Подключить транспорт |
| `voice:produce` | client → server | `{roomId, transportId, kind, rtpParameters, appData}` | Начать трансляцию (audio/video/screen) |
| `voice:consume` | client → server | `{roomId, producerId, rtpCapabilities}` | Получить чужой поток |
| `voice:resume` | client → server | `{roomId, consumerId}` | Возобновить consumer |
| `voice:setConsumerLayer` | client → server | `{consumerId, spatialLayer, temporalLayer}` | Качество видео |
| `voice:mute` | client → server | `{roomId, muted}` | Мьют/анмьют |
| `voice:deafen` | client → server | `{roomId, deafened}` | Оглушить (не слышать других) |
| `voice:chat` | client → server | `{roomId, text}` | Сообщение в чат комнаты |
| `voice:kick` | client → server | `{roomId, userId}` | Кикнуть участника |
| `voice:restartIce` | client → server | `{transportId}` | Перезапуск ICE (реконнект) |
| `voice:muteAll` | client → server | `{roomId}` | Замьютить всех (владелец) |
| `voice:consumerClosed` | server → client | `{consumerId}` | Consumer закрыт |
| `voice:chat:error` | server → client | `{error}` | Ошибка в чате комнаты |

---

## 3. Шрифты

### Nekst (заголовки / display)
Файлы TTF (для Flutter):
- `Nekst-Thin.ttf`
- `Nekst-Light.ttf`
- `Nekst-Regular.ttf`
- `Nekst-Medium.ttf`
- `Nekst-SemiBold.ttf`
- `Nekst-Bold.ttf`
- `Nekst-Black.ttf`

Расположение: `client/src/assets/fonts/`
Также есть WOFF/WOFF2 (для веба, Flutter не нужны).

### Onest (основной текст / body)
Файлы TTF (для Flutter):
- `Onest-Regular.ttf`
- `Onest-Medium.ttf`
- `Onest-SemiBold.ttf`

Расположение: `client/src/assets/fonts/`

**Действие для Flutter:** скопировать .ttf файлы в `flutter_app/assets/fonts/`, подключить в `pubspec.yaml`.

---

## 4. GLSL-шейдеры

**Отдельных файлов .frag/.vert/.glsl нет.**

Шейдеры встроены inline в JavaScript-компоненте MetaballBackground (Three.js / raymarching). При миграции на Flutter нужно будет переписать шейдеры в отдельные .frag файлы для `FragmentProgram.fromAsset()`.

Основные эффекты для воссоздания:
- Raymarching метаболы (фон приложения)
- Chromatic aberration на краях
- Idle breathing (замедление при бездействии)
- Event-reactive pulse (пульсация при событиях)
- Eco-mode fallback (CSS-градиент вместо шейдера)

---

## 5. Экраны и компоненты приложения

### Основные экраны

| Экран | Файл | Описание |
|-------|------|----------|
| AuthScreen | `auth/AuthScreen.jsx` | Контейнер авторизации (login/register/verify/2fa/forgot) |
| MainScreen | `main/MainScreen.jsx` | Основной layout (TopNav + Sidebar + Content) |
| ChatView | `chat/ChatView.jsx` | Экран чата (сообщения + ввод) |
| ChannelView | `channels/ChannelView.jsx` | Экран канала (посты + настройки) |
| FriendsScreen | `friends/FriendsScreen.jsx` | Друзья (все/онлайн/запросы) |
| SettingsScreen | `settings/SettingsScreen.jsx` | Настройки (все секции) |
| CallScreen | `voice/CallScreen.jsx` | Экран звонка (аудио/видео) |
| NebulaView | `nebula/NebulaView.jsx` | Dashboard / Spaces |
| AboutScreen | `settings/AboutScreen.jsx` | О программе |
| FeedbackScreen | `settings/FeedbackScreen.jsx` | Обратная связь |

### Auth-компоненты
- `LoginForm` — форма входа (email/username + password)
- `RegisterForm` — форма регистрации
- `VerifyForm` — верификация email (ввод кода)
- `TwoFactorForm` — ввод 2FA-кода
- `ForgotPasswordFlow` — сброс пароля (3 шага)
- `PasswordCard` — компонент поля пароля (eye toggle)
- `GravityCard` — glass-карточка авторизации

### Chat-компоненты
- `ChatHub` — контейнер для Sidebar + ChatView
- `ChatCard` — карточка чата в списке
- `ChatHeader` — хедер чата (имя, кнопки звонка, поиск, меню)
- `ChatInput` — поле ввода (текст, файлы, голосовые, GIF, emoji)
- `ChatMessage` — пузырь сообщения (текст, статусы, pin, reply, reactions)
- `MediaMessage` — медиа-вложение (фото, файл, PDF, видео, аудио)
- `MessageActionsPill` — меню действий (reply, forward, react, edit, delete, pin, copy, report)
- `AttachmentPreview` — превью перед отправкой
- `ImageLightbox` — полноэкранный просмотр картинок
- `LinkPreviewCard` — OG-превью ссылок
- `MentionSuggestions` — автодополнение @упоминаний
- `GifPicker` — поиск и выбор GIF
- `CreateChatModal` — создание нового чата/группы
- `GroupMembersPanel` — список участников группы

### Channel-компоненты
- `ChannelBrowser` — обзор каналов (grid, категории, поиск)
- `ChannelCard` — карточка канала
- `ChannelPost` — пост в канале
- `CreateChannelModal` — создание канала
- `ChannelMembersModal` — подписчики канала

### Voice-компоненты
- `VoiceRoomList` — список голосовых комнат
- `VoiceRoom` — внутри комнаты (участники, управление)
- `VoiceControls` — панель управления (мьют, камера, screen share)
- `VoiceChat` — текстовый чат внутри голосовой комнаты
- `IncomingCallOverlay` — входящий звонок (overlay)
- `CallBanner` — баннер активного звонка
- `ScreenSharePicker` — выбор экрана/окна для демонстрации
- `VideoGrid` — сетка видеопотоков

### Profile-компоненты
- `ProfileCard` — карточка профиля (аватар, имя, био, кнопки)
- `ProfileEditor` — редактирование профиля
- `AvatarCropModal` — обрезка аватара

### UI-компоненты (переиспользуемые)
- `DynamicIsland` — навигационная капсула (8 состояний, анимации)
- `TopNav` — верхняя навигация (pill-табы, аватар, поиск, уведомления)
- `NotificationsPanel` — панель уведомлений
- `SidebarNormal` — боковая панель (полная)
- `SidebarCollapsed` — боковая панель (свёрнутая)
- `HoverPreview` — превью чата при наведении
- `SpotlightSearch` — Ctrl+K поиск (command palette)
- `SpotlightProfile` — быстрый профиль из spotlight
- `ConfirmDialog` — диалог подтверждения
- `EmptyState` — пустые состояния
- `NavShelf` — полка навигации
- `NotificationBell` — колокольчик уведомлений
- `ShieldFingerprint` — отпечаток E2E-ключа
- `UpdateBanner` / `UpdateToast` — уведомления об обновлениях

### Panels
- `VibeMeter` — настроение чата (виджет)
- `OrbitPanel` — orbit UI

### Admin
- `AdminPanel` — контейнер (10 секций)
- Секции: Overview, Users, Tags, Moderation, Channels, Feedback, Logs, Broadcast, Database, ServerSettings

---

## 6. Phosphor Icons (120+ уникальных)

Flutter-пакет: `phosphor_flutter`

Полный список используемых иконок:

```
AppWindow, Archive, ArrowBendUpLeft, ArrowBendUpRight, ArrowClockwise,
ArrowLeft, ArrowRight, ArrowSquareOut, ArrowUp, ArrowsInSimple,
ArrowsOutSimple, At, Atom, Bell, BellSlash, Bug, Calendar, Camera,
CaretDown, CaretLeft, CaretRight, CaretUp, ChatCircle, ChatCircleDots,
ChatDots, Check, CheckCircle, Checks, ClipboardText, Clock, Cloud,
Compass, Confetti, Cpu, Crown, Database, Desktop, DeviceMobile,
DotsThree, DotsThreeOutline, DownloadSimple, Drop, Envelope, Eye,
EyeSlash, FileText, FilePdf, FilmSlate, FilmStrip, Fire, Flag,
GameController, GearSix, Gif, GithubLogo, Globe, GraduationCap,
HandPointing, HandWaving, Hash, Headphones, Heart, House, Image,
ImageBroken, ImageSquare, Info, Key, Lightning, Lightbulb, LinkSimple,
List, Lock, MagnifyingGlass, MagnifyingGlassMinus, MagnifyingGlassPlus,
Megaphone, Microphone, MicrophoneSlash, Minus, MinusCircle, Monitor,
MonitorArrowUp, MusicNote, MusicNotes, Newspaper, Palette,
PaperPlaneTilt, Paperclip, Pause, PenNib, PencilSimple, Phone,
PhoneDisconnect, PhoneIncoming, PhoneX, Planet, Play, PlayCircle, Plus,
Prohibit, ProhibitInset, Pulse, PushPin, Question, Radio, Rocket,
Scales, Shield, ShieldCheck, SignIn, SignOut, Sliders, Smiley,
SmileySticker, Sparkle, SpeakerHigh, SpeakerSlash, SpinnerGap, Square,
TextT, Trash, Trophy, Tray, User, UserCheck, UserCircle, UserMinus,
UserPlus, UsersThree, VideoCamera, VideoCameraSlash, Warning,
WarningCircle, WifiHigh, WifiLow, WifiMedium, WifiSlash, X, XCircle
```

---

## 7. Цветовая палитра (из CSS custom properties)

```
--bg-app:          #0a0a0f          (фон приложения)
--surface-0:       #12121a          (базовая поверхность)
--surface-1:       #1a1a24          (приподнятая)
--surface-2:       #22222e          (ещё выше)
--surface-3:       #2a2a38          (максимальная)
--accent:          #c8ff00          (GOTBLESK green)
--accent-hover:    #d4ff33
--text-primary:    rgba(255,255,255,0.87)
--text-secondary:  rgba(255,255,255,0.60)
--text-tertiary:   rgba(255,255,255,0.45)
--text-disabled:   rgba(255,255,255,0.25)
--online:          #4ade80
--danger:          #ef4444
--warning:         #f59e0b
```

Светлая тема: `--accent` остаётся `#c8ff00`, текст на акценте чёрный, фон `#f0f2f5`.

---

## 8. Типографика

| Роль | Шрифт | Размер | Вес |
|------|-------|--------|-----|
| Display | Nekst | 32px | Black (900) |
| Headline | Nekst | 24px | Bold (700) |
| Title | Nekst | 18px | SemiBold (600) |
| Subtitle | Onest | 15px | SemiBold (600) |
| Body | Onest | 15px | Regular (400) |
| Caption | Onest | 11px | Medium (500) |

---

## 9. Border Radius

| Токен | Значение |
|-------|----------|
| sm | 8px |
| md | 14px |
| lg | 22px |
| pill | 100px |

---

## 10. Чеклист готовности к Фазе 1

- [x] Все REST API задокументированы (100+ эндпоинтов)
- [x] Все WebSocket-события задокументированы (40+ событий)
- [x] Шрифты найдены (10 .ttf файлов для копирования)
- [x] GLSL-шейдеры: отдельных файлов нет, inline в MetaballBackground — нужно переписать
- [x] Все экраны задокументированы (10 экранов, 60+ компонентов)
- [x] Все Phosphor-иконки задокументированы (120+ уникальных)
- [x] Цвета и типографика задокументированы
- [ ] Flutter SDK установлен и `flutter doctor` зелёный
- [ ] Dart изучен (базовый уровень)
