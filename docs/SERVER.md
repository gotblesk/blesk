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

Ещё можно обновлять сервер из GitHub. Для этого сервер один раз готовят скриптом `scripts/server-bootstrap.sh`, а в настройки репозитория добавляют секреты `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` и `VPS_KNOWN_HOSTS` — ключ сервера, по которому GitHub его узнаёт (подробно — «Деплой из GitHub» в техническом приложении). После этого каждая сборка веток `main`, `master` и `claude/file-review-0lky6f` сама ставится на сервер blesk.su вместе с сайтом, а **Actions → Deploy server → Run workflow** запускает установку вручную.

## 5. Звонки

Звонки работают через LiveKit — он ставится вместе с сервером, ничего делать не нужно. Разговор шифруется на телефонах, LiveKit только передаёт его и прослушать не может.

Если у хостера есть свой файрвол в панели управления, откройте в нём:

- **7881** TCP;
- **50000–50100** UDP.

Без этих портов звонки не соединятся, а переписка будет работать как обычно.

## 6. Резервные копии и переезд

Ночная зашифрованная копия в S3-хранилище у другого провайдера (база, вложения, пароли из `deploy/.env` и сертификат) включается секретами `BACKUP_*` в GitHub (пошагово — `docs/NEEDS_HUMAN.md`, 2.7): следующий деплой ставит таймер и сразу делает первую копию. **На 22.10.2026 секретов нет, и копий на api.blesk.su нет.**

- Сделать копию сейчас: `sudo bash /opt/blesk/scripts/backup.sh`.
- Проверить, что копия восстанавливается: **Actions → Backup check → Run workflow**. Эта кнопка, как и ежедневная проверка и кнопка **Deploy server**, появится, когда основной веткой репозитория станет рабочая (`NEEDS_HUMAN.md`, 2.4): GitHub запускает расписания и ручные запуски только оттуда. Без секретов проверка красная.
- Переехать на другой сервер или к другому хостеру: `docs/MIGRATION.md`.

## 7. Мониторинг

Каждые 15 минут GitHub Actions (`monitor.yml`) проверяет снаружи `https://api.blesk.su/health` и `https://blesk.su/`, три попытки с паузой в минуту. Если не отвечает хоть один, открывается обращение «Сервер не отвечает» с упоминанием владельца, и GitHub присылает письмо; когда оба ответят, обращение закрывается само. Как и проверка копий, это расписание заработает после смены основной ветки (`NEEDS_HUMAN.md`, 2.4).

## Техническое приложение

**Резервные копии** (`scripts/backup.sh`, `restore.sh`, `restore-drill.sh`, общая часть — `backup-lib.sh`): restic 0.17 в контейнере, репозиторий `s3:<BACKUP_S3_ENDPOINT>/<BACKUP_S3_BUCKET>/blesk`, шифрование restic (AES-256-CTR + Poly1305, ключ из `BACKUP_PASSWORD` через scrypt). В снимке: `pg_dump --format=custom`, том `attachments`, `.env`, `certs`. Хост снимков всегда `blesk`, поэтому новый сервер видит снимки старого. Таймер `blesk-backup.timer` — 03:30 по Москве; хранение 7/4/6 (дни, недели, месяцы); по воскресеньям `restic check --read-data-subset=10%`. Настройки — `deploy/backup.env` (права 600, вне git), пишет его `deploy.sh` из переменных `BACKUP_*`. `BLESK_RESTORE=latest` при запуске `deploy.sh` восстанавливает сервер из копии; поверх существующих данных — только с `BLESK_RESTORE_FORCE=1`.

**Защита хоста** (`deploy.sh`): ufw открывает только SSH, 80/TCP, 443/TCP, 443/UDP и порты LiveKit (7881/TCP, 50000–50100/UDP). fail2ban следит за SSH (`/etc/fail2ban/jail.d/blesk.conf`: 5 ошибок за 10 минут — бан на час), автоматические обновления безопасности включены в apt (`20auto-upgrades`); деплой проверяет, что fail2ban следит за SSH и таймер обновлений работает, и останавливается с ошибкой, если нет. Системный журнал хранится 7 дней и не больше 1 ГБ (`/etc/systemd/journald.conf.d/blesk.conf`), туда же идут журналы контейнеров. С одного адреса на 80 и 443 — не больше 200 соединений сразу и 50 новых в секунду (запас 200): правила в цепочке `DOCKER-USER` ставит `scripts/connlimit.sh` при каждом старте Docker (`blesk-connlimit.service`), потому что опубликованные порты Docker идут мимо ufw. Таймер `blesk-watchdog` раз в минуту перезапускает контейнеры blesk, которые не прошли проверку здоровья. При `BLESK_SSH_KEYS_ONLY=1` (так запускает GitHub) — вход по SSH только по ключу: `/etc/ssh/sshd_config.d/10-blesk.conf` с `PasswordAuthentication no`, `KbdInteractiveAuthentication no` и `PermitRootLogin prohibit-password`. Это делается, только если ключи есть у root и, когда пользователь `deploy` уже создан, у него; иначе вход по паролю остаётся и в журнале будет предупреждение.

**Подготовка сервера** (`scripts/server-bootstrap.sh`, один раз от root, повторный запуск ничего не ломает):

```
bash server-bootstrap.sh '<строка из blesk_owner.pub>' '<строка из blesk_deploy.pub>'
```

Скрипт проверяет, что обе строки — открытые SSH-ключи и что они разные. Ключ владельца он добавляет в `/root/.ssh/authorized_keys`. Создаёт пользователя `deploy` (оболочка `/bin/bash`, пароля нет): в `/home/deploy/.ssh/authorized_keys` — только ключ деплоя с пометкой `restrict` (без проброса портов и агента, без терминала), из ключей root этот ключ убирается. Ставит `/usr/local/sbin/blesk-deploy`: команда сбрасывает `BLESK_REPO`, `BLESK_DIR` и `BLESK_BUILD_HERE`, отказывается от имени ветки с `..` или с `/` либо `-` в начале, запускает `/opt/blesk/scripts/deploy.sh`, а на чистом сервере скачивает `deploy.sh` ветки `BLESK_BRANCH` (по умолчанию `master`, адрес `…/refs/heads/<ветка>/…`) с GitHub во временный файл. Пишет `/etc/sudoers.d/blesk-deploy` и проверяет его `visudo -cf`, а потом весь sudo — `visudo -c`:

```
Defaults!/usr/local/sbin/blesk-deploy env_reset, env_keep = "BLESK_BRANCH BLESK_COMMIT BLESK_DOMAIN BLESK_SITE_DOMAIN BLESK_RESTORE BLESK_SSH_KEYS_ONLY BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_REGION BACKUP_S3_ACCESS_KEY BACKUP_S3_SECRET_KEY BACKUP_PASSWORD"
deploy ALL=(root) NOPASSWD: /usr/local/sbin/blesk-deploy
```

sudo запускает команду с чистым окружением и оставляет в нём только эти настройки деплоя, поэтому ни `BASH_ENV`, ни `LD_PRELOAD`, ни `BLESK_REPO` подсунуть нельзя: ключ деплоя позволяет только поставить ветку `gotblesk/blesk` с этими настройками (включая адрес и пароль резервных копий), но не выполнить свой код от root. Эти же имена экспортирует `scripts/ci/remote-deploy.sh` (`FORWARDED` и `BLESK_SSH_KEYS_ONLY`); новое имя добавляется в оба места, а на сервер попадает повторным запуском `server-bootstrap.sh`. В конце скрипт печатает ключ хоста из `/etc/ssh/ssh_host_ed25519_key.pub` — это значение секрета `VPS_KNOWN_HOSTS`. Вход по паролю скрипт не выключает — это делает первый деплой. Ключи на ноутбуке владельца — `~/.ssh/blesk_owner` (комментарий `blesk-owner`) и `~/.ssh/blesk_deploy` (`blesk-deploy`), ed25519 без пароля; закрытый ключ деплоя — секрет `VPS_SSH_KEY`, `VPS_USER=deploy`, `VPS_HOST=94.228.175.178`.

**Деплой из GitHub.** В `build.yml` после сборки APK идёт задача `deploy`: только для веток `main`, `master` и `claude/file-review-0lky6f` и только при секретах `VPS_HOST`, `VPS_USER` и `VPS_SSH_KEY` (без них — пометка в журнале, сервер не трогается). Она заходит по SSH (`scripts/ci/remote-deploy.sh`, `BatchMode`) и запускает `sudo -n /usr/local/sbin/blesk-deploy`, а если `VPS_USER=root` — `bash /opt/blesk/scripts/deploy.sh`. Переменные: `BLESK_BRANCH`, `BLESK_COMMIT` (ровно собранный коммит), `BLESK_DOMAIN=api.blesk.su`, `BLESK_SITE_DOMAIN=blesk.su`, `BLESK_SSH_KEYS_ONLY=1` и `BACKUP_*`, если заданы; значения экранируются, идут по SSH через stdin и экспортируются в окружение, а не пишутся в командную строку sudo. Деплои идут по одному (группа `deploy`): новый push отменяет незаконченную сборку, но не идущий деплой. `deploy.yml` делает то же вручную, с выбором доменов, другого хоста и переноса из копии; расписания и ручные запуски GitHub выполняет только на основной ветке репозитория.

**Ключ сервера** (`VPS_KNOWN_HOSTS`). По SSH уходят секреты резервных копий, поэтому сервер должен доказать, что он тот самый: ssh подключается со `StrictHostKeyChecking=yes` и принимает только ключ из секрета. Значение — строка known_hosts (`94.228.175.178 ssh-ed25519 AAAA…`) или просто ключ (`ssh-ed25519 AAAA…`, его печатает `server-bootstrap.sh`), тогда он считается ключом `VPS_HOST`. Без секрета ключ берётся `ssh-keyscan` у того, кто ответил, — с предупреждением и отпечатком в журнале; так же `deploy.yml` подключается к другому хосту (`host`). После переустановки сервера ключ хоста меняется: секрет нужно обновить, иначе деплой остановится с ошибкой проверки ключа.

**Проверки после деплоя** (`scripts/ci/check-live.sh`, с GitHub, повторы до 3 минут): `https://api.blesk.su/health` отвечает `{"status":"ok"}`; `https://blesk.su` — 200 со словами «Блестящий мессенджер»; `https://www.blesk.su` перенаправляет на `https://blesk.su/`; `/download` отдаёт APK файлом; `/.well-known/assetlinks.json` — JSON с `app.blesk` и `Content-Type: application/json`; сертификаты blesk.su, www.blesk.su и api.blesk.su действуют ещё хотя бы 14 дней. Итог — таблицей в сводке запуска.

**Состав** (`deploy/docker-compose.yml`): PostgreSQL 18.6, сервер blesk (Kotlin, Ktor, на `eclipse-temurin:21.0.12.1_1-jre-noble`), LiveKit 1.13.7, Caddy 2.11.7 (он же отдаёт сайт); версии закреплены точно и меняются отдельным коммитом. У сервера есть проверка здоровья: `curl /health` каждые 30 с, после трёх неудач подряд контейнер считается нездоровым, и его перезапускает `blesk-watchdog`. Caddy ограничивает заголовки (10 с, 32 КБ), тело запроса (2 минуты; до 16 МБ, одно вложение целиком — до 220 МБ) и простой соединения (5 минут); у API, как и у сайта, есть HSTS. Наружу открыты порты **80** и **443** (TCP, а для HTTP/3 ещё 443/UDP) и порты медиа LiveKit: **7881/TCP** и **50000–50100/UDP**. Порт 80 нужен для выпуска сертификата и перенаправления на HTTPS. `deploy.sh` открывает все эти порты в ufw, если он включён.

**Звонки.** Сигнализация LiveKit идёт через Caddy: `wss://<хост>/livekit` проксируется на `livekit:7880` без префикса. Ключ и секрет LiveKit `deploy.sh` создаёт один раз и хранит в `deploy/.env` (`LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`), в контейнер они попадают через `LIVEKIT_KEYS`; в `deploy/livekit.yaml` секретов нет. Сервер blesk выдаёт токен LiveKit (JWT HS256, 6 часов, одна комната, identity — имя пользователя). Медиа шифруется на клиентах ключом из Signal-сообщения или состояния группы. Журнал LiveKit — только предупреждения, чтобы в нём не было имён и комнат.

**TLS.** С доменом Caddy сам получает и продлевает сертификат Let's Encrypt, с сайтом — для API, сайта и www. Без домена `deploy.sh` создаёт самоподписанный сертификат: ECDSA P-256, 10 лет, SAN — IP сервера. Он лежит в `deploy/certs`. В QR-код попадает `blesk://server?url=https://<ip>&pin=sha256/<base64>`, где pin — SHA-256 от SubjectPublicKeyInfo в формате OkHttp `CertificatePinner`. С доменом в QR-коде только `blesk://server?url=https://<домен>`.

**API** (JSON, байты в Base64, контракт — `shared/.../protocol/Api.kt`):

| Метод | Путь | Доступ |
|---|---|---|
| GET | `/health` | открыто, без лимита; 200 `{"status":"ok"}`, если база ответила на `SELECT 1` за 2 с, иначе 503 `{"status":"unavailable"}`. Все остальные запросы — под общим лимитом с одного адреса (600 в минуту, запас 300); ответ 429 несёт `Retry-After` |
| GET | `/v1/info` | открыто; `ServerInfo`: версия, звонки, `invites` (true — регистрация по приглашению), `minClientVersion` (самая старая версия приложения, которую сервер обслуживает), `officialChannel` (адрес официального канала или null) |
| GET | `/v1/usernames/{name}` | открыто; свой лимит: 40 сразу и 20 в минуту с одного IP, с токеном — 30 и 15 на аккаунт. Ответ `UsernameCheck`: `available`, `verdict` (`free`, `taken`, `reserved`, `invalid`), `username` — как сравнил сервер (строчными), `suggestions` — до трёх свободных вариантов. Недействительный токен — 401, приложение повторяет без него |
| POST | `/v1/accounts` | открыто, лимит запросов; при `BLESK_REGISTRATION=invite` — только с действующим приглашением в поле `invite` (код или ссылка). Имя с заглавными или служебное — 400 `invalid_username` (ещё до проверки приглашения), оканчивающееся на `_bot` — 400 `username_reserved` |
| POST | `/v1/auth/challenge`, `/v1/auth/token` | открыто, лимит запросов; токен — только подписью ключа этого аккаунта |
| POST | `/v1/account/recover` | открыто, лимит запросов; нужен токен восстановления |
| GET | `/v1/accounts/{username}` | токен; скрытый аккаунт — только с `?link=`; 20 поисков сразу, дальше 10 в минуту на человека (иначе 429) |
| DELETE | `/v1/account`; PUT `/v1/account/recovery`, `/v1/discovery` | токен; только свой аккаунт |
| PUT | `/v1/keys` | токен; ключи подписаны ключом личности этого аккаунта |
| GET | `/v1/keys/status`, `/v1/keys/{username}` | токен; скрытый аккаунт — только с `?link=`; одноразовых ключей одному человеку — 5 сразу, потом 1 в час, дальше связка без них |
| POST | `/v1/messages/{username}` | токен |
| POST | `/v1/attachments` (тело `application/octet-stream`) | токен |
| POST | `/v1/attachments/uploads` | токен; загрузка по частям записывается на того, кто её начал |
| PUT, GET, POST | `/v1/attachments/{id}/chunks/{n}`, `/chunks`, `/complete` | токен того, кто начал загрузку |
| GET | `/v1/attachments/{id}` | токен; только законченные; id — 128 случайных бит, файл зашифрован |
| WebSocket | `/v1/ws` (токен в `Authorization` или `?token=`; в адресе — только здесь) | токен; подтверждения — только своих сообщений |
| POST | `/v1/calls/token` (тело `{"room": "..."}`, ответ `{"url", "token"}`) | токен, 30 в минуту на аккаунт; identity — всегда имя вызывающего |
| GET | `/v1/invites` (ответ `InviteList`) | токен; только свои: действующие неиспользованные и использованные за последние 30 дней (`usedBy` — кто пришёл), новые сначала; `left` — сколько ещё можно создать |
| POST | `/v1/invites` (ответ 201 `InviteInfo`) | токен; приглашение на 7 дней, не больше 5 неиспользованных сразу (иначе 409 `invite_limit`); лимит запросов с одного IP общий с регистрацией |

**Каналы и комнаты** (`/v1/spaces`, контракт — `Spaces.kt`, без E2EE). Для всех запросов нужен токен.

| Метод | Путь | Кто может |
|---|---|---|
| POST | `/v1/spaces` | любой; создатель становится владельцем. Адрес не по шаблону — 400 `invalid_handle`; служебный (blesk и похожие, служебные слова, `mine`) — 409 `handle_taken` |
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
| PATCH | `/v1/spaces/{handle}/posts/{id}` | автор, пока он может писать здесь (не забанен, не вышел, в канале — ещё админ) |
| DELETE | `/v1/spaces/{handle}/posts/{id}` | автор; владелец и админ — если автор ниже по роли или уже не участник |

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

Ошибки приходят как `{"code": "...", "message": ""}`. Коды: `bad_request`, `invalid_username`, `invalid_key`, `invalid_signature`, `username_taken`, `unauthorized`, `auth_failed`, `not_found`, `no_keys`, `too_many_keys`, `too_large`, `rate_limited`, `internal`, а для каналов и комнат ещё `invalid_handle`, `handle_taken`, `forbidden`, `not_member`, `banned`, `owner_cannot_leave`, `invalid_role`, `not_a_room`, `too_many_channels`, `last_channel`, `unknown_attachment`, для звонков `invalid_room` и `calls_unavailable` (503, LiveKit не настроен; тогда и `/v1/info` отвечает `"calls": false`), а для ботов `username_reserved` (и для имён ботов, похожих на служебные), `invalid_name`, `too_many_bots`, `invalid_text`, `invalid_buttons`, `invalid_commands`, `invalid_webhook`, `chat_not_started`, а для приглашений `invite_required` и `invite_invalid` (403: приглашения нет; оно неизвестно, использовано или истекло) и `invite_limit` (409).

Вложения и недоставленные сообщения удаляются через 30 дней. Вложение публичного поста хранится, пока существует сам пост. Истёкшие приглашения удаляются, а использованные — через 30 дней после регистрации по ним, вместе с тем, кто по ним пришёл. Токены действуют 30 дней. В журнале сервера пишутся только метод, шаблон пути, код ответа и время ответа.

**Переменные окружения сервера:**

| Переменная | Значение |
|---|---|
| `BLESK_PORT` | порт HTTP, по умолчанию 8080 |
| `BLESK_DB_URL`, `BLESK_DB_USER`, `BLESK_DB_PASSWORD` | подключение к PostgreSQL (JDBC) |
| `BLESK_STORAGE` | `minio` (по умолчанию) или `fs`; `deploy/` ставит `fs` |
| `BLESK_MINIO_ENDPOINT`, `BLESK_MINIO_ACCESS_KEY`, `BLESK_MINIO_SECRET_KEY`, `BLESK_MINIO_BUCKET` | MinIO; бакет создаётся сам |
| `BLESK_FS_DIR` | папка для вложений при `fs` |
| `BLESK_TRUST_PROXY` | `true` за Caddy: адрес клиента берётся из `X-Forwarded-For` |
| `BLESK_REQUEST_RATE_BURST`, `BLESK_REQUEST_RATE_PER_MINUTE` | общий лимит запросов с одного IP, кроме `/health` (300 и 600) |
| `BLESK_AUTH_RATE_BURST`, `BLESK_AUTH_RATE_PER_MINUTE` | лимит на регистрацию и вход с одного IP (20 и 30) |
| `BLESK_USERNAME_CHECK_RATE_BURST`, `BLESK_USERNAME_CHECK_RATE_PER_MINUTE` | проверка имени на лету без входа, с одного IP (40 и 20); со входом — 30 и 15 на аккаунт |
| `BLESK_POST_RATE_PER_MINUTE` | лимит постов в каналах и комнатах на аккаунт (30) |
| `LIVEKIT_PUBLIC_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | LiveKit: адрес для телефонов (`wss://<хост>/livekit`), ключ и секрет. Нужны все три или ни одной; без них звонки выключены |
| `BLESK_CALL_RATE_PER_MINUTE` | лимит токенов звонков на аккаунт (30) |
| `BLESK_BOT_RATE_PER_MINUTE` | лимит сообщений ботам и создания ботов на аккаунт (60) |
| `BLESK_BOT_API_RATE_PER_MINUTE` | лимит запросов к Bot API на бота (300) |
| `BLESK_REGISTRATION` | `invite` (по умолчанию) — регистрация только по приглашению; `open` — кто угодно, присланное приглашение тогда не тратится |

**Служебные имена.** Имена людей, адреса каналов и комнат и имена ботов, похожие на blesk и служебные слова (admin, support, system, moderator, official и другие — с цифрами вместо букв, с точками и _, с цифрами на конце), занять нельзя. Аккаунты, взявшие такое имя раньше, по-прежнему входят, находятся и получают сообщения. Каждый ответ 429, в том числе изнутри маршрутов (поиск, звонки, посты, боты, Bot API, приглашения), говорит `Retry-After`.

**Официальный канал.** `server-ssh official <имя>` с ноутбука (или на сервере `docker compose exec -T server /opt/blesk-server/bin/blesk-server official <имя>`) делает канал @blesk официальным, а этот аккаунт — его владельцем; повторный запуск ничего не меняет. Пометка хранится в базе (`spaces.official`, миграция `V8__official_space.sql`), переменной окружения нет, поэтому другие серверы с тем же `deploy/` официального канала не получают. Коды выхода: 0 — готово (печатает `@blesk`), 2 — неверная команда, 3 — нет такого аккаунта, 4 — @blesk принадлежит другому аккаунту, 5 — @blesk у этого аккаунта комната, а не канал.

**Приглашения владельца.** Первому человеку пригласить некому, поэтому есть приглашения без владельца: они не истекают. `blesk-server invites` печатает ссылки на неиспользованные, по одной в строке (`https://blesk.su/i/<код>`); при самом первом запуске сначала создаёт 10. `blesk-server invites add <n>` (от 1 до 50) создаёт ещё n и печатает все неиспользованные. Команда подключается к базе по тем же переменным, применяет миграции и завершается, сервер не запускает; в stdout — только ссылки, журнал идёт в stderr; ошибка в аргументах — код выхода 2. В `deploy/`: `cd /opt/blesk/deploy && docker compose exec server /opt/blesk-server/bin/blesk-server invites`.

**Переменные `deploy.sh`:** `BLESK_BRANCH` (по умолчанию `master`); `BLESK_COMMIT` — полный SHA, чтобы поставить ровно этот коммит; он ставится, только если входит в последние 50 коммитов ветки (по одному SHA GitHub отдал бы и коммит из чужого форка), иначе берётся последний коммит ветки; `BLESK_DOMAIN` — домен API; `BLESK_SITE_DOMAIN` — домен сайта, только вместе с `BLESK_DOMAIN` (оба сохраняются в `deploy/.env`, пустое значение выключает); `BLESK_PUBLIC_IP`; `BLESK_DIR` (по умолчанию `/opt/blesk`); `BLESK_SSH_KEYS_ONLY=1`; `BLESK_BUILD_HERE=1`; `BLESK_RESTORE`; `BACKUP_*`. Пример: `curl ... | sudo BLESK_DOMAIN=chat.example.ru bash`. Готовность сервера `deploy.sh` ждёт по `/health`.

**Сайт** (`BLESK_SITE_DOMAIN`, `deploy/Caddyfile.site`). `api.blesk.su` — API и сигнализация звонков, `blesk.su` — сайт, `www.blesk.su` перенаправляет на `https://blesk.su` с тем же путём; у всех трёх A-запись на IP сервера. При каждом деплое `deploy.sh` собирает сайт из `site/` командой `python3 scripts/build-site.py --out … --release release.json`, берёт `release.json` и `blesk.apk` из релиза того же коммита (SHA-256 APK сверяется с `release.json`) и кладёт APK в `download/blesk.apk`. Сборка идёт рядом, в `deploy/site-dist.next`; когда контейнеры запустились, она целиком подменяет `deploy/site-dist`, Caddy пересоздаётся, и только после этого прежняя папка удаляется — неудачный деплой оставляет сайт таким, каким он был. Caddy видит папку как `/srv/site` (только чтение). Caddy пересоздаётся при каждом деплое, LiveKit — только если изменился `deploy/livekit.yaml`, чтобы не обрывать звонки: привязанные файл или папка остаются теми, что были при создании контейнера, а git и сборка сайта кладут новые.

| Адрес | Что отдаёт |
|---|---|
| `/`, `/security`, `/privacy`, `/terms` | страницы (`<путь>/index.html`) |
| `/i/<код>`, `/@<имя>`, `/c/<имя>`, `/r/<код>` | `invite/index.html` — «Тебя пригласили в blesk»; если blesk установлен, Android открывает ссылку сразу в приложении |
| `/.well-known/assetlinks.json` | отпечаток ключа подписи для Android App Links |
| `/download`, `/download/` | перенаправление на `/download/blesk.apk`, файл с `Content-Disposition: attachment` |
| остальное | `404.html` с кодом 404 |

Заголовки сайта: CSP `default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; font-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` (поэтому на сайте нет встроенных `<script>` и `style=""`), `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `Permissions-Policy`, HSTS на год. Сжатие zstd и gzip. Шрифты кешируются на 30 дней, всё остальное — `Cache-Control: no-cache` (браузер переспрашивает по ETag). Без `BLESK_SITE_DOMAIN` всё как раньше: `Caddyfile.domain` (только API) или `Caddyfile.ip` (без домена).

**release.json** лежит в каждом релизе рядом с `blesk.apk` и `blesk-server.tar.gz`: `version`, `versionCode`, `tag`, `commit`, `apkSize`, `apkSha256`, `signerSha256` (SHA-256 сертификата подписи, строчными, без двоеточий), `minAndroid`. Пишет его `scripts/ci/release-json.sh` (версия и minSdk — из APK через aapt2, ключ — через apksigner), сайт проверяется на нём же ещё в CI (`build-site.py --check`).

**Вложения.** В `deploy/` вложения лежат файлами в томе `attachments` (`BLESK_STORAGE=fs`): они зашифрованы на устройствах, серверу достаточно хранить байты. MinIO остаётся для своих установок (`BLESK_STORAGE=minio`). Образы Docker Hub сначала берутся через `mirror.gcr.io` (`/etc/docker/daemon.json`, если его ещё нет).

**Сборка без Docker:** `./gradlew :server:installDist`, затем `server/build/install/blesk-server/bin/blesk-server`. Тесты: `./gradlew :server:test`. Они поднимают настоящий PostgreSQL через embedded-postgres.

**Сборка сервера.** На VPS сервер не собирается: для сборки нужен Maven-репозиторий Signal, заблокированный в России. `build.yml` прикладывает к каждому релизу `blesk-server.tar.gz`, `deploy.sh` (`fetch_server`) скачивает архив релиза ровно своего коммита в `server/dist/` (если сборка ещё идёт — ждёт до часа) и пишет в `.env` `BLESK_SERVER_DOCKERFILE=server/Dockerfile.dist`. `BLESK_BUILD_HERE=1` собирает на месте по `server/Dockerfile`.
