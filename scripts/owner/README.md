# Скрипты для ноутбука

Каждый скрипт есть в двух видах: `.ps1` для Windows (PowerShell) и `.sh` для macOS (Терминал). Запускай их из папки репозитория: `cd $HOME\blesk` в Windows, `cd ~/blesk` в macOS. Перед запуском обнови репозиторий: `git pull`.

Windows:

```
powershell -ExecutionPolicy Bypass -File scripts\owner\install-latest.ps1
```

macOS:

```
bash scripts/owner/install-latest.sh
```

Для других скриптов замени имя файла.

| Скрипт | Что делает |
|---|---|
| `install-latest` | Скачивает последнюю сборку blesk с GitHub, сверяет контрольную сумму и ставит на телефон по кабелю. |
| `emulator-b` | Запускает эмулятор — второй телефон «Б» (Pixel, Android 14) — и ставит на него последнюю сборку. В первый раз сам создаёт эмулятор. |
| `logs` | Сохраняет журнал blesk с телефона в файл `blesk-reports/blesk-log-….txt` на рабочем столе. |
| `screenrecord` | Записывает видео экрана телефона в `blesk-reports/blesk-video-….mp4` на рабочем столе. Остановить — Enter. |
| `server-ssh` | Заходит на сервер. С командой `invites` — ссылки-приглашения (`invites add 5` — ещё пять), `status` — работает ли сервер, `logs` — журнал сервера. |
| `server-setup` | Один раз: делает ключи для входа на сервер и для GitHub, настраивает сервер (спросит пароль root) и копирует ключ для GitHub в буфер обмена. Когда вставишь ключ в GitHub, нажми Enter — скрипт очистит буфер. |

Дополнительные ключи:

- `logs` и `screenrecord` с эмулятора «Б»: `-Emulator` в Windows, `--emulator` в macOS.
- `install-latest` и `emulator-b`, если скрипт сказал, что новую сборку можно поставить только с удалением старой: `-Force` в Windows, `--force` в macOS. Старая копия blesk удалится вместе с данными на этом устройстве.
- `server-ssh`: команда пишется после имени файла, например `powershell -ExecutionPolicy Bypass -File scripts\owner\server-ssh.ps1 invites` или `bash scripts/owner/server-ssh.sh invites`.

Если что-то не работает, пришли скриншот окна с ошибкой. Для ошибки в самом blesk — видео (`screenrecord`) и журнал (`logs`).
