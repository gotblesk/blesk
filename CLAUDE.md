# blesk

Конфиденциальный мессенджер для Android. Единственный источник правды — `docs/BRIEF.md`.

## В начале каждой сессии

1. Прочитай `docs/BRIEF.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`.
2. Продолжай с первого незакрытого пункта `docs/PROGRESS.md`. «Продолжай» от владельца значит именно это.
3. Баг, лог или скриншот от владельца — сначала исправь, потом продолжай этап.

## Правила

- Работай автономно, вопросов не задавай. Неясное решай сам и записывай в `docs/DECISIONS.md`.
- Нужен человек (аккаунт, секрет, сервер) — пошаговая инструкция в `docs/NEEDS_HUMAN.md` для ноутбука владельца на Windows (PowerShell в Терминале, пути `C:\Users\…`, без macOS и Linux; телефон — только для проверки приложения), дальше делай всё остальное.
- Никаких следов ассистента в коммитах, PR, коде и документации. После коммита проверяй `git log -1 --format=%B`.
- Коммиты на английском, Conventional Commits, короткие.
- Код senior-уровня: понятные имена, комментарии только про неочевидное «почему», без TODO, без закомментированного кода, без эмодзи.
- Никаких кнопок-пустышек: неготовое скрыто или помечено `SoonTag`/`ListRow(soon = true)`.
- Все строки интерфейса — в ресурсах, по-русски. Доступность (TalkBack, крупный шрифт) с первого экрана.
- Своя криптография запрещена. Ключи — Android Keystore, база — SQLCipher. В логах нет текста сообщений, ключей и контактов (`EventLog` принимает только коды).
- Репозиторий открыт всем: в нём не бывает паролей, токенов, ключей, ссылок-инвайтов, IP-адресов чужих машин и личных данных владельца. CI проверяет всю историю gitleaks; найденное — сразу в `docs/NEEDS_HUMAN.md` с просьбой сменить, файл — удалить.
- Всё, что не E2EE, матовое и подписанное. Блик (`Modifier.glint`) — только у E2EE.
- Стоп-лист из раздела 7 брифа проверяется перед каждым релизом по скриншотам.
- Отчёт владельцу: по-русски, коротко, без техники: что сделано, ссылка на APK, что проверить, что пока не работает.

## Сборка

- Android SDK ставит `scripts/setup-android.sh` (SessionStart-хук). В облачной сессии Maven Central подменяется зеркалом через `scripts/gradle/central-mirror.init.gradle.kts`.
- Перед push: `python3 scripts/ci/screenshots.py fetch`, затем `./gradlew detekt testDebugUnitTest :shared:test :detekt-rules:test :server:test :server:installDist :app:compareRoborazziDebug :app:assembleRelease`, затем `python3 scripts/ci/screenshots.py check <папка>` и `scripts/ci/integration.sh` (настоящий сервер в Docker). Эталон скриншотов — снимки последнего релиза; намеренно изменённый экран называется в `docs/RELEASE_NOTES.md` строкой «Вид изменён (сравнение со сборкой N): …». Снимки этой сборки — в папке из `check`, смотри их глазами.
- Релизы делает `.github/workflows/build.yml` на каждый push; описание релиза — `docs/RELEASE_NOTES.md`, обнови его перед push.
- В облачной сессии Docker-демон не запущен: `nohup dockerd >/tmp/dockerd.log 2>&1 &` перед `integration.sh`. Если Docker Hub отвечает 429, образ берётся с `mirror.gcr.io/<имя>` и получает прежний тег (`docker tag`).
- CI на каждый push: всё из проверки выше, плюс дым-тест релизного APK на эмуляторе (`scripts/ci/smoke.sh`). Упавший шаг останавливает релиз и деплой.

## Устройство кода

- `:shared` — модели протокола и формат темы (kotlinx.serialization), общие с сервером.
- `:core:design` — «Три блеска»: OKLCH-палитры и контраст (`color`), темы (`theme`), свет и искра (`spark`), иконки (`icon`), компоненты (`component`).
- `:core:motion` — токены анимаций (`BleskMotion`, `Durations`, кривая `Viscous`) и словарь вибраций (`Haptics`: tick, stick, detach, success, refuse, warn, heavy, menu, toggle). Экраны вызывают вибрацию по смыслу, чисел в экранах нет.
- `:core:data` — Room + SQLCipher (`db`), модели, интерфейсы мира чатов (`ChatRepository`, `GroupController`, `SpaceController`, `BotController`, `KeyVerification`, `calls`), настройки, демо-мир, журнал отладки.
- `:core:crypto` — libsignal (`signal`: PQXDH, Double Ratchet, Sender Keys, номера безопасности), шифрование вложений, PIN, ключ базы.
- `:core:network` — REST и WebSocket сервера, пиннинг ключа (`BleskApi`, `Socket`).
- `:core:media` — фото, видео, голосовые, кружки, закрытая папка медиа.
- `:core:messaging` — серверный режим: `ServerChatRepository` и движки групп, пространств, ботов, ключей.
- `:core:calls` — звонки на LiveKit со сквозным шифрованием кадров (`CallMedia`, `CallManager`).
- `:feature:*` — экраны (chats, calls, settings, security, profile, debug).
- `:app` — навигация (Navigation 3), `AppContainer`, звонки (`calls`: активность, сервис, уведомления, core-telecom), фон (`background`: уведомления, «всегда на связи»).
- `:third-party:audioswitch` — исходники форка AudioSwitch, нужного LiveKit (на Maven его нет), подставляются вместо JitPack.
- `:server` — Ktor + PostgreSQL + Flyway, вложения файлами на диске (или MinIO), LiveKit-токены, Bot API, инвайты, `/health`; `deploy/` и `scripts/deploy.sh` — установка на VPS.
- `:integration` — тест клиентов против настоящего сервера в Docker (`scripts/ci/integration.sh`); без сервера задача пропускается.
- `site/` — статический сайт blesk.su (без фреймворков), собирает `scripts/build-site.py`; Caddy отдаёт его с основного сервера.
- `scripts/owner/` — скрипты владельца для ноутбука (главные — `.ps1` для Windows, `.sh` — запасные): установка APK, журнал, эмулятор «Б», вход на сервер, запись экрана.
