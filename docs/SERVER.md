# Сервер blesk

Сервер пересылает зашифрованные сообщения и хранит зашифрованные файлы. Прочитать их он не может: ключи есть только у телефонов.

Исключение — публичные каналы и открытые комнаты. Они не шифруются: их посты сервер хранит открытым текстом, чтобы их мог читать кто угодно.

## 1. Купить сервер

Подойдёт любой VPS с такими параметрами:

- система **Ubuntu 24.04** (или 22.04);
- процессор **x86_64** (он же amd64). ARM не подойдёт;
- **2 ГБ** памяти, от 20 ГБ диска;
- публичный IP-адрес.

После покупки хостер пришлёт IP-адрес и пароль root.

Домен не обязателен. Если он есть, добавьте у регистратора A-запись на IP сервера ещё до установки.

## 2. Установить

Зайдите на сервер по SSH (с телефона подойдёт Termius или JuiceSSH):

```
ssh root@IP-сервера
```

Выполните одну команду:

```
curl -fsSL https://raw.githubusercontent.com/gotblesk/blesk/master/scripts/deploy.sh | sudo bash
```

Скрипт спросит домен. Если домена нет, просто нажмите Enter.

Установка занимает 5–10 минут. В конце на экране появится QR-код.

## 3. Подключить приложение

В приложении откройте **Настройки → Сервер → Сканировать QR** и наведите камеру на QR-код в терминале.

Если сервер без домена, в QR-коде есть отпечаток сертификата. По нему приложение узнаёт ваш сервер, и подменить его незаметно нельзя.

QR-код можно показать ещё раз в любой момент: запустите ту же команду установки.

## 4. Обновить

Запустите ту же команду ещё раз. Переписка, файлы, пароли и сертификат сохранятся.

Ещё можно обновить сервер из GitHub: **Actions → Deploy server → Run workflow**. Перед этим нужно один раз добавить в настройки репозитория секреты `VPS_HOST`, `VPS_USER` и `VPS_SSH_KEY`.

## 5. Звонки

Звонки работают через LiveKit — он ставится вместе с сервером, ничего делать не нужно. Разговор шифруется на телефонах, LiveKit только передаёт его и прослушать не может.

Если у хостера есть свой файрвол в панели управления, откройте в нём:

- **7881** TCP;
- **50000–50100** UDP.

Без этих портов звонки не соединятся, а переписка будет работать как обычно.

## 6. Резервные копии

Все данные лежат в двух томах Docker:

- `blesk_postgres-data` — аккаунты, открытые ключи, недоставленные сообщения;
- `blesk_minio-data` — зашифрованные вложения.

Сделать копию:

```
cd /opt/blesk/deploy
docker compose stop server
docker run --rm -v blesk_postgres-data:/v -v /root:/b alpine tar czf /b/blesk-postgres.tgz -C /v .
docker run --rm -v blesk_minio-data:/v -v /root:/b alpine tar czf /b/blesk-minio.tgz -C /v .
docker compose start server
```

Скачайте оба архива из `/root` и ещё сохраните файл `/opt/blesk/deploy/.env` (пароли) и папку `/opt/blesk/deploy/certs` (сертификат). Без сертификата приложениям придётся заново сканировать QR.

Восстановление: та же команда, только `tar xzf` в пустой том при остановленных контейнерах.

## Техническое приложение

**Состав** (`deploy/docker-compose.yml`): PostgreSQL, MinIO, сервер blesk (Kotlin, Ktor), LiveKit, Caddy. Наружу открыты порты **80** и **443** (TCP, а для HTTP/3 ещё 443/UDP) и порты медиа LiveKit: **7881/TCP** и **50000–50100/UDP**. Порт 80 нужен для выпуска сертификата и перенаправления на HTTPS. `deploy.sh` открывает все эти порты в ufw, если он включён.

**Звонки.** Сигнализация LiveKit идёт через Caddy: `wss://<хост>/livekit` проксируется на `livekit:7880` без префикса. Ключ и секрет LiveKit `deploy.sh` создаёт один раз и хранит в `deploy/.env` (`LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`), в контейнер они попадают через `LIVEKIT_KEYS`; в `deploy/livekit.yaml` секретов нет. Сервер blesk выдаёт токен LiveKit (JWT HS256, 6 часов, одна комната, identity — имя пользователя). Медиа шифруется на клиентах ключом из Signal-сообщения или состояния группы. Журнал LiveKit — только предупреждения, чтобы в нём не было имён и комнат.

**TLS.** С доменом Caddy сам получает и продлевает сертификат Let's Encrypt. Без домена `deploy.sh` создаёт самоподписанный сертификат: ECDSA P-256, 10 лет, SAN — IP сервера. Он лежит в `deploy/certs`. В QR-код попадает `blesk://server?url=https://<ip>&pin=sha256/<base64>`, где pin — SHA-256 от SubjectPublicKeyInfo в формате OkHttp `CertificatePinner`. С доменом в QR-коде только `blesk://server?url=https://<домен>`.

**API** (JSON, байты в Base64, контракт — `shared/.../protocol/Api.kt`):

| Метод | Путь | Доступ |
|---|---|---|
| GET | `/v1/info` | открыто |
| POST | `/v1/accounts` | открыто, лимит запросов |
| POST | `/v1/auth/challenge`, `/v1/auth/token` | открыто, лимит запросов |
| GET | `/v1/accounts/{username}` | токен |
| PUT | `/v1/keys` | токен |
| GET | `/v1/keys/status`, `/v1/keys/{username}` | токен |
| POST | `/v1/messages/{username}` | токен |
| POST | `/v1/attachments` (тело `application/octet-stream`) | токен |
| GET | `/v1/attachments/{id}` | токен |
| WebSocket | `/v1/ws` (токен в `Authorization` или `?token=`) | токен |
| POST | `/v1/calls/token` (тело `{"room": "..."}`, ответ `{"url", "token"}`) | токен, 30 в минуту на аккаунт |

**Каналы и комнаты** (`/v1/spaces`, контракт — `Spaces.kt`, без E2EE). Для всех запросов нужен токен.

| Метод | Путь | Кто может |
|---|---|---|
| POST | `/v1/spaces` | любой; создатель становится владельцем |
| GET | `/v1/spaces?query=`, `/v1/spaces/mine` | любой |
| GET | `/v1/spaces/{handle}` | любой |
| PATCH | `/v1/spaces/{handle}` | владелец, админ |
| DELETE | `/v1/spaces/{handle}` | владелец |
| POST | `/v1/spaces/{handle}/join`, `/leave` | любой, кроме забаненных; владелец не может выйти |
| GET | `/v1/spaces/{handle}/members?offset=&limit=` | участники |
| POST | `/v1/spaces/{handle}/roles` | владелец; админ — только по отношению к участникам |
| POST, DELETE | `/v1/spaces/{handle}/ban/{username}` | владелец, админ; только тех, кто ниже по роли |
| POST | `/v1/spaces/{handle}/channels` | владелец, админ; только в комнатах, не больше 20 |
| DELETE | `/v1/spaces/{handle}/channels/{id}` | владелец, админ; последний удалить нельзя |
| POST | `/v1/spaces/{handle}/posts` | в канале — владелец и админы, в комнате — любой участник |
| GET | `/v1/spaces/{handle}/posts?channel=&before=&limit=` | любой, новые сначала |
| PATCH | `/v1/spaces/{handle}/posts/{id}` | автор |
| DELETE | `/v1/spaces/{handle}/posts/{id}` | автор, владелец, админ |

Новые, изменённые и удалённые посты приходят по WebSocket всем участникам в кадрах `post` и `post_changed`. В очередь эти кадры не попадают: после переподключения клиент сам загружает историю. Повтор поста с тем же `clientId` не создаёт новый пост. Посты ограничены 30 в минуту на аккаунт.

**Боты** (контракт — `Bots.kt`, без E2EE, подробно — [`BOT_API.md`](BOT_API.md)). Имена ботов заканчиваются на `_bot`; людям такие имена регистрировать нельзя (`username_reserved`).

| Метод | Путь | Доступ |
|---|---|---|
| POST | `/v1/bots`, GET `/v1/bots/mine` | токен; до 20 ботов на владельца |
| POST | `/v1/bots/{username}/token`, DELETE `/v1/bots/{username}` | токен владельца |
| GET | `/v1/bots/{username}` | токен |
| POST | `/v1/bots/{username}/updates` | токен; 60 в минуту на аккаунт |
| GET, POST | `/bot/v1/{getMe, getUpdates, sendMessage, editMessage, answerCallback, setWebhook, deleteWebhook, setCommands}` | токен бота в `Authorization: Bearer`; 300 в минуту на бота |

Сервер хранит только SHA-256 токена бота. Бот пишет только тем, кто писал ему сам. Ответы ботов идут человеку конвертами типа 100 через ту же очередь. Вебхуки — только `https` на публичные адреса, без перенаправлений; неудачная доставка оставляет обновление для `getUpdates`.

Ошибки приходят как `{"code": "...", "message": ""}`. Коды: `bad_request`, `invalid_username`, `invalid_key`, `invalid_signature`, `username_taken`, `unauthorized`, `auth_failed`, `not_found`, `no_keys`, `too_many_keys`, `too_large`, `rate_limited`, `internal`, а для каналов и комнат ещё `invalid_handle`, `handle_taken`, `forbidden`, `not_member`, `banned`, `owner_cannot_leave`, `invalid_role`, `not_a_room`, `too_many_channels`, `last_channel`, `unknown_attachment`, для звонков `invalid_room` и `calls_unavailable` (503, LiveKit не настроен; тогда и `/v1/info` отвечает `"calls": false`), а для ботов `username_reserved`, `invalid_name`, `too_many_bots`, `invalid_text`, `invalid_buttons`, `invalid_commands`, `invalid_webhook`, `chat_not_started`.

Вложения и недоставленные сообщения удаляются через 30 дней. Вложение публичного поста хранится, пока существует сам пост. Токены действуют 30 дней. В журнале сервера пишутся только метод, шаблон пути, код ответа и время ответа.

**Переменные окружения сервера:**

| Переменная | Значение |
|---|---|
| `BLESK_PORT` | порт HTTP, по умолчанию 8080 |
| `BLESK_DB_URL`, `BLESK_DB_USER`, `BLESK_DB_PASSWORD` | подключение к PostgreSQL (JDBC) |
| `BLESK_STORAGE` | `minio` (по умолчанию) или `fs` |
| `BLESK_MINIO_ENDPOINT`, `BLESK_MINIO_ACCESS_KEY`, `BLESK_MINIO_SECRET_KEY`, `BLESK_MINIO_BUCKET` | MinIO; бакет создаётся сам |
| `BLESK_FS_DIR` | папка для вложений при `fs` |
| `BLESK_TRUST_PROXY` | `true` за Caddy: адрес клиента берётся из `X-Forwarded-For` |
| `BLESK_AUTH_RATE_BURST`, `BLESK_AUTH_RATE_PER_MINUTE` | лимит на регистрацию и вход с одного IP (20 и 30) |
| `BLESK_POST_RATE_PER_MINUTE` | лимит постов в каналах и комнатах на аккаунт (30) |
| `LIVEKIT_PUBLIC_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | LiveKit: адрес для телефонов (`wss://<хост>/livekit`), ключ и секрет. Нужны все три или ни одной; без них звонки выключены |
| `BLESK_CALL_RATE_PER_MINUTE` | лимит токенов звонков на аккаунт (30) |
| `BLESK_BOT_RATE_PER_MINUTE` | лимит сообщений ботам и создания ботов на аккаунт (60) |
| `BLESK_BOT_API_RATE_PER_MINUTE` | лимит запросов к Bot API на бота (300) |

**Переменные `deploy.sh`:** `BLESK_BRANCH` (по умолчанию `master`), `BLESK_DOMAIN`, `BLESK_PUBLIC_IP`, `BLESK_DIR` (по умолчанию `/opt/blesk`). Пример: `curl ... | sudo BLESK_DOMAIN=chat.example.ru bash`.

**MinIO.** Официальных образов MinIO на Docker Hub больше нет. Поэтому по умолчанию используется `cgr.dev/chainguard/minio`: его собирают из исходников MinIO. Другой образ можно указать в `MINIO_IMAGE` в `deploy/.env`.

**Сборка без Docker:** `./gradlew :server:installDist`, затем `server/build/install/blesk-server/bin/blesk-server`. Тесты: `./gradlew :server:test`. Они поднимают настоящий PostgreSQL через embedded-postgres.
