# blesk

Конфиденциальный мессенджер для Android. Главный источник правды — `docs/BRIEF.md`.

## В начале каждой сессии

1. Прочитай `docs/BRIEF.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`.
2. Продолжай с первого незакрытого пункта `docs/PROGRESS.md`. «Продолжай» от владельца значит именно это.
3. Баг, лог или скриншот от владельца — сначала исправь, потом продолжай этап.

## Правила

- Работай автономно, вопросов не задавай. Неясное решай сам и записывай в `docs/DECISIONS.md`.
- Нужен человек (аккаунт, секрет, сервер) — пошаговая инструкция с телефона в `docs/NEEDS_HUMAN.md`, дальше делай всё остальное.
- Никаких следов ассистента в коммитах, PR, коде и документации. После коммита проверяй `git log -1 --format=%B`.
- Коммиты на английском, Conventional Commits, короткие.
- Код senior-уровня: понятные имена, комментарии только про неочевидное «почему», без TODO, без закомментированного кода, без эмодзи.
- Никаких кнопок-пустышек: неготовое скрыто или помечено `SoonTag`/`ListRow(soon = true)`.
- Все строки интерфейса — в ресурсах, по-русски. Доступность (TalkBack, крупный шрифт) с первого экрана.
- Своя криптография запрещена. Ключи — Android Keystore, база — SQLCipher. В логах нет текста сообщений, ключей и контактов (`EventLog` принимает только коды).
- Всё, что не E2EE, матовое и подписанное. Блик (`Modifier.glint`) — только у E2EE.
- Стоп-лист из раздела 5 брифа проверяется перед каждым релизом по скриншотам.
- Отчёт владельцу: по-русски, коротко, без техники: что сделано, ссылка на APK, что проверить, что пока не работает.

## Сборка

- Android SDK ставит `scripts/setup-android.sh` (SessionStart-хук). В облачной сессии Maven Central подменяется зеркалом через `scripts/gradle/central-mirror.init.gradle.kts`.
- Перед push: `./gradlew detekt testDebugUnitTest :app:recordRoborazziDebug :app:assembleRelease`. Скриншоты — в `app/build/screenshots`, смотри их глазами.
- Релизы делает `.github/workflows/build.yml` на каждый push; описание релиза — `docs/RELEASE_NOTES.md`, обнови его перед push.

## Устройство кода

- `:shared` — модели протокола и формат темы (kotlinx.serialization), общие с сервером.
- `:core:design` — «Три блеска»: OKLCH-палитры и контраст (`color`), темы (`theme`), свет и искра (`spark`), иконки (`icon`), компоненты (`component`).
- `:core:data` — Room + SQLCipher (`db`), модели, интерфейсы мира чатов (`ChatRepository`, `GroupController`, `SpaceController`, `BotController`, `KeyVerification`, `calls`), настройки, демо-мир, журнал отладки.
- `:core:crypto` — libsignal (`signal`: PQXDH, Double Ratchet, Sender Keys, номера безопасности), шифрование вложений, PIN, ключ базы.
- `:core:network` — REST и WebSocket сервера, пиннинг ключа (`BleskApi`, `Socket`).
- `:core:media` — фото, видео, голосовые, кружки, закрытая папка медиа.
- `:core:messaging` — серверный режим: `ServerChatRepository` и движки групп, пространств, ботов, ключей.
- `:core:calls` — звонки на LiveKit со сквозным шифрованием кадров (`CallMedia`, `CallManager`).
- `:feature:*` — экраны (chats, calls, settings, security, profile, debug).
- `:app` — навигация (Navigation 3), `AppContainer`, звонки (`calls`: активность, сервис, уведомления, core-telecom), фон (`background`: уведомления, «всегда на связи»).
- `:server` — Ktor + PostgreSQL + Flyway, MinIO, LiveKit-токены, Bot API; `deploy/` и `scripts/deploy.sh` — установка на VPS.
