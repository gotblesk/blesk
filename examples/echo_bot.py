#!/usr/bin/env python3
"""Эхо-бот для blesk: только стандартная библиотека Python 3.

Повторяет текст собеседника, на /start отвечает сообщением с кнопками, а нажатие кнопки
подтверждает коротким уведомлением и меняет само сообщение.

Запуск:
    export BLESK_URL=https://chat.example.ru
    export BLESK_BOT_TOKEN='echo_bot:...'
    python3 echo_bot.py

Сервер с самоподписанным сертификатом (без домена):
    export BLESK_CA_FILE=/opt/blesk/deploy/certs/blesk.crt   # сертификат сервера
    # или, только для проверки на своём стенде:
    export BLESK_INSECURE=1                                   # без проверки сертификата
"""

import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request

POLL_SECONDS = 50


def make_ssl_context():
    ca_file = os.environ.get("BLESK_CA_FILE")
    if ca_file:
        return ssl.create_default_context(cafile=ca_file)
    if os.environ.get("BLESK_INSECURE") == "1":
        # Только для тестов: без проверки сертификата кто угодно в сети может подменить сервер и украсть токен.
        print("ВНИМАНИЕ: проверка сертификата выключена (BLESK_INSECURE=1). Только для тестов!", file=sys.stderr)
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        return context
    return ssl.create_default_context()


class BleskError(Exception):
    def __init__(self, status, code):
        super().__init__(f"{status} {code}")
        self.status = status
        self.code = code


class Bot:
    def __init__(self, base_url, token):
        self.api = base_url.rstrip("/") + "/bot/v1/"
        self.token = token
        self.context = make_ssl_context()

    def call(self, method, body=None, query="", timeout=30):
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(
            self.api + method + query,
            data=data,
            method="GET" if body is None else "POST",
            headers={"Authorization": "Bearer " + self.token, "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout, context=self.context) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            try:
                code = json.loads(error.read() or b"{}").get("code", "")
            except ValueError:
                code = ""
            raise BleskError(error.code, code) from None
        return json.loads(raw) if raw else None

    def get_updates(self, offset):
        query = f"?offset={offset}&timeout={POLL_SECONDS}"
        return self.call("getUpdates", query=query, timeout=POLL_SECONDS + 10)

    def send(self, chat, text, buttons=None):
        return self.call("sendMessage", {"chat": chat, "text": text, "buttons": buttons or []})["messageId"]

    def edit(self, chat, message_id, text, buttons=None):
        self.call("editMessage", {"chat": chat, "messageId": message_id, "text": text, "buttons": buttons or []})

    def answer(self, callback_id, notice=None):
        self.call("answerCallback", {"callbackId": callback_id, "notice": notice})


START_BUTTONS = [
    [{"text": "Сказать привет", "data": "hello"}, {"text": "Который час?", "data": "time"}],
    [{"text": "Что такое blesk", "url": "https://github.com/gotblesk/blesk"}],
]


def handle_message(bot, message):
    chat, text = message["from"], message["text"]
    if text.strip() == "/start":
        bot.send(chat, "Привет! Я повторяю всё, что вы пишете. А ещё умею нажимать кнопки:", START_BUTTONS)
    else:
        bot.send(chat, text)


def handle_callback(bot, callback):
    chat, data = callback["from"], callback["data"]
    if data == "hello":
        bot.answer(callback["id"], "Привет!")
        bot.edit(chat, callback["messageId"], "Вы нажали «Сказать привет».", START_BUTTONS)
    elif data == "time":
        now = time.strftime("%H:%M")
        bot.answer(callback["id"], "Сейчас " + now)
        bot.edit(chat, callback["messageId"], "На сервере бота сейчас " + now + ".", START_BUTTONS)
    else:
        bot.answer(callback["id"])


def main():
    url = os.environ.get("BLESK_URL")
    token = os.environ.get("BLESK_BOT_TOKEN")
    if not url or not token:
        sys.exit("Задайте BLESK_URL и BLESK_BOT_TOKEN")
    bot = Bot(url, token)
    me = bot.call("getMe")
    print("Бот @" + me["username"] + " запущен", file=sys.stderr)

    offset = 0
    while True:
        try:
            updates = bot.get_updates(offset)
        except BleskError as error:
            if error.status == 401:
                sys.exit("Токен не подходит: создайте новый в приложении")
            print("Ошибка сервера:", error, file=sys.stderr)
            time.sleep(5)
            continue
        except (urllib.error.URLError, OSError) as error:
            print("Нет связи с сервером:", error, file=sys.stderr)
            time.sleep(5)
            continue

        for update in updates:
            # Следующий запрос с offset больше этого номера подтвердит обновление, и сервер его удалит.
            offset = update["updateId"] + 1
            try:
                if update.get("message"):
                    handle_message(bot, update["message"])
                elif update.get("callback"):
                    handle_callback(bot, update["callback"])
            except BleskError as error:
                print("Не удалось ответить:", error, file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
