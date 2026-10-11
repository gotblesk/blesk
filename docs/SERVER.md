# Сервер blesk

Сервер пересылает зашифрованные сообщения и хранит зашифрованные файлы. Прочитать их он не может: ключи есть только у телефонов.

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

## 5. Резервные копии

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

**Состав** (`deploy/docker-compose.yml`): PostgreSQL, MinIO, сервер blesk (Kotlin, Ktor), Caddy. Наружу открыты только порты **80** и **443** (TCP, а для HTTP/3 ещё 443/UDP). Порт 80 нужен для выпуска сертификата и перенаправления на HTTPS.

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

Ошибки приходят как `{"code": "...", "message": ""}`. Коды: `bad_request`, `invalid_username`, `invalid_key`, `invalid_signature`, `username_taken`, `unauthorized`, `auth_failed`, `not_found`, `no_keys`, `too_many_keys`, `too_large`, `rate_limited`, `internal`.

Вложения и недоставленные сообщения удаляются через 30 дней. Токены действуют 30 дней. В журнале сервера пишутся только метод, шаблон пути, код ответа и время ответа.

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

**Переменные `deploy.sh`:** `BLESK_BRANCH` (по умолчанию `master`), `BLESK_DOMAIN`, `BLESK_PUBLIC_IP`, `BLESK_DIR` (по умолчанию `/opt/blesk`). Пример: `curl ... | sudo BLESK_DOMAIN=chat.example.ru bash`.

**MinIO.** Официальных образов MinIO на Docker Hub больше нет. Поэтому по умолчанию используется `cgr.dev/chainguard/minio`: его собирают из исходников MinIO. Другой образ можно указать в `MINIO_IMAGE` в `deploy/.env`.

**Сборка без Docker:** `./gradlew :server:installDist`, затем `server/build/install/blesk-server/bin/blesk-server`. Тесты: `./gradlew :server:test`. Они поднимают настоящий PostgreSQL через embedded-postgres.
