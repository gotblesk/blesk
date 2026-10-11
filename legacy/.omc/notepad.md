# Notepad
<!-- Auto-managed by OMC. Manual edits preserved in MANUAL section. -->

## Priority Context
<!-- ALWAYS loaded. Keep under 500 chars. Critical discoveries only. -->

## Working Memory
<!-- Session notes. Auto-pruned after 7 days. -->
### 2026-04-02 05:21
СЛЕДУЮЩАЯ СЕССИЯ — Редизайн профилей blesk:
1. User Dropdown — добавить характер (не просто список)
2. Профиль другого юзера — нужна уникальность, место для тегов/коллекции/статуса аккаунта
3. Свой профиль (blesk card) — фронт пустой, нужен контент (статистика, теги, уровень)
4. Редактирование профиля — форма без дизайна, нужен glass-стиль
5. Подготовить UI для будущих фич: теги, коллекция, blesk coins, статус аккаунта
6. НЕ ЗАБЫТЬ подключить ВСЕ скиллы перед каждой подзадачей
### 2026-04-02 12:55
### 2026-04-02 13:01
## Полный аудит blesk — визуал + навигация + UX (02.04.2026)

### BROKEN (не работает — 4)
B-1: MainScreen — handleOpenChat принимает userId от Friends/ProfilePopover, но ожидает chatId → белый экран
B-2: Каналы — deep link на канал игнорирует параметр
B-3: Sidebar — pin/mute hover кнопки без onClick handlers
B-4: ChatHeader — search и video call кнопки не рендерятся (пропсы не пробрасываются)

### MISSING (отсутствует — 8)
M-1: Нет "удалить из друзей" в UI
M-2: Нет "заблокировать" в UI
M-3: Нет пагинации истории сообщений (scroll вверх)
M-4: Нет пагинации постов канала
M-5: Нет кнопки "создать чат" в main UI
M-6: Нет мьюта чата
M-7: Нет контекстного меню на сайдбар чатах
M-8: E2E shield badge не подключён в ChatView

### LIGHT THEME (17 файлов без поддержки!)
- OrbitPanel, VibeMeter, все Voice компоненты, AdminPanel, модалки/оверлеи — белый текст на белом фоне
- ChatMessage: timestamps, sender names, reply text — rgba(255,255,255,...) на белом
- global.css: нет --surface-0..3, --voice-bar-inactive для light theme

### DESIGN SYSTEM (низкое adoption)
- Spacing tokens (--space-1..16) определены но почти не используются в компонентах
- border-radius tokens (--radius-sm/md/lg) определены но hardcoded px в большинстве CSS
- Duration tokens (--duration-fast/normal) определены но hardcoded ms

### EDGE CASES (9)
E-1: undefined chat fallback — нет UI при несуществующем чате
E-2: userId/chatId путаница в Friends
E-3: Escape key конфликты между модалками
E-4: Voice room polling при неактивной комнате
E-5: Тихий провал загрузки файлов
E-6: Нет лимита длины сообщения на клиенте
E-7: Бесконечный retry без backoff
E-8: undefined participant data
E-9: Double-click subscribe race condition

### UX ISSUES (10)
U-1: Переключение табов не сохраняет состояние
U-2: Нет индикатора отправки сообщения
U-3: Неоднозначная подсветка активного чата в sidebar
U-4: Settings не помнит последний таб
U-5: Disabled call button без объяснения почему
U-6: Задержка показа offline banner
U-7: Отписка от канала без подтверждения
U-8: Нет проверки на дублирующееся имя при создании группы
U-9: Поиск в sidebar без debounce
U-10: Навигация теряет несохранённые изменения профиля


## 2026-04-02 05:21
СЛЕДУЮЩАЯ СЕССИЯ — Редизайн профилей blesk:
1. User Dropdown — добавить характер (не просто список)
2. Профиль другого юзера — нужна уникальность, место для тегов/коллекции/статуса аккаунта
3. Свой профиль (blesk card) — фронт пустой, нужен контент (статистика, теги, уровень)
4. Редактирование профиля — форма без дизайна, нужен glass-стиль
5. Подготовить UI для будущих фич: теги, коллекция, blesk coins, статус аккаунта
6. НЕ ЗАБЫТЬ подключить ВСЕ скиллы перед каждой подзадачей
### 2026-04-02 12:55
## Подтверждённые баги (аудит 02.04.2026)

### CRITICAL
1. server/src/middleware/csrf.js:52 — CSRF bypass: origin=null → next()
2. client/src/hooks/useSocket.js:65-66 — Stale closure reconnect, msg.id undefined
3. server/src/routes/auth.js:453 — Dummy bcrypt hash невалидный, timing leak

### HIGH
4. client/src/store/callStore.js:92-101 — Phantom call при уходе последнего участника
5. client/src/hooks/useSocket.js:464-469 — Batch reactions payload mismatch
6. server/src/ws/chatHandler.js:358-404 — message:delete дубль DB + auth по user chatId
7. client/src/components/chat/ChatView.jsx:158 — Stale socketRef в markAsRead

### MEDIUM
8. client/src/store/channelStore.js:74 — .reverse() мутация + O(N*M)
9. client/src/App.jsx:265-275 — ErrorBoundary не на AuthScreen
10. client/src/App.jsx:207-215 — setTimeout state on unmounted
11. server/src/routes/users.js:98-99 — Мутация Prisma объекта
12. client/src/components/voice/VoiceRoom.jsx:148-149 — stale videoRef cleanup
13. client/src/styles/global.css — Светлая тема: нет --surface-0..3
14. client/src/styles/global.css — Светлая тема: нет --voice-bar-inactive


## 2026-04-02 05:21
СЛЕДУЮЩАЯ СЕССИЯ — Редизайн профилей blesk:
1. User Dropdown — добавить характер (не просто список)
2. Профиль другого юзера — нужна уникальность, место для тегов/коллекции/статуса аккаунта
3. Свой профиль (blesk card) — фронт пустой, нужен контент (статистика, теги, уровень)
4. Редактирование профиля — форма без дизайна, нужен glass-стиль
5. Подготовить UI для будущих фич: теги, коллекция, blesk coins, статус аккаунта
6. НЕ ЗАБЫТЬ подключить ВСЕ скиллы перед каждой подзадачей


## MANUAL
<!-- User content. Never auto-pruned. -->

