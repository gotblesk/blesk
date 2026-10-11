package app.blesk.server

import app.blesk.server.bots.WebhookPolicy
import app.blesk.shared.protocol.AnswerCallback
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallbackPress
import app.blesk.shared.protocol.EditMessage
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.InlineButton
import app.blesk.shared.protocol.SendMessage
import app.blesk.shared.protocol.SentMessage
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SetCommands
import app.blesk.shared.protocol.SetWebhook
import app.blesk.shared.protocol.Update
import app.blesk.shared.protocol.UserToBot
import com.sun.net.httpserver.HttpServer
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class BotApiTest {
    @Test
    fun `what people write comes out of getUpdates and an offset acknowledges it`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        assertEquals(HttpStatusCode.NoContent, harness.writeToBot(alice, bot.bot.username, text = "привет", id = "m1").status)
        // The same id again is a retry, not a second message.
        assertEquals(HttpStatusCode.NoContent, harness.writeToBot(alice, bot.bot.username, text = "привет", id = "m1").status)
        harness.writeToBot(alice, bot.bot.username, text = "/start", id = "m2")

        val first = harness.getUpdates(bot.token)
        assertEquals(listOf("привет", "/start"), first.map { it.message?.text })
        assertEquals(listOf("m1", "m2"), first.map { it.message?.id })
        assertEquals(alice.username, first[0].message?.from)
        assertTrue(first[0].updateId < first[1].updateId)
        assertEquals(first, harness.getUpdates(bot.token))

        val rest = harness.getUpdates(bot.token, offset = first[1].updateId)
        assertEquals(listOf(first[1]), rest)
        assertEquals(emptyList<Update>(), harness.getUpdates(bot.token, offset = first[1].updateId + 1))
        assertEquals(emptyList<Update>(), harness.getUpdates(bot.token))
    }

    @Test
    fun `a long poll returns as soon as an update arrives`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        coroutineScope {
            val started = TimeSource.Monotonic.markNow()
            val poll = async { harness.getUpdates(bot.token, timeout = 30) }
            delay(300)
            harness.writeToBot(alice, bot.bot.username, text = "ау")
            val updates = withTimeout(10.seconds) { poll.await() }
            assertEquals("ау", updates.single().message?.text)
            assertTrue(started.elapsedNow() < 10.seconds)
        }
    }

    @Test
    fun `an empty long poll waits for its timeout`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val started = TimeSource.Monotonic.markNow()
        assertEquals(emptyList<Update>(), harness.getUpdates(bot.token, timeout = 1))
        assertTrue(started.elapsedNow() >= 1.seconds)
        assertEquals(HttpStatusCode.BadRequest, harness.botGet(bot.token, "getUpdates?timeout=${Bots.MAX_POLL_SECONDS + 1}").status)
    }

    @Test
    fun `a bot message reaches the person over the socket as a bot envelope`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        harness.uploadKeys(alice, harness.keys(alice, preKeys = 25, kyberPreKeys = 25))
        harness.writeToBot(alice, bot.bot.username, text = "/start")

        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(alice.token) }) {
            assertEquals(ServerFrame.Synced, nextFrame())
            val buttons = listOf(
                listOf(InlineButton("Да", data = "yes"), InlineButton("Сайт", url = "https://example.com")),
            )
            val sent = harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, "Привет!", buttons)).body<SentMessage>()

            val envelope = (nextFrame() as ServerFrame.Deliver).envelope
            assertEquals(bot.bot.username, envelope.sender)
            assertEquals(EnvelopeType.BOT, envelope.type)
            assertEquals(BotMessage(sent.messageId, "Привет!", buttons), envelope.botMessage())
        }
    }

    @Test
    fun `a bot message waits in the queue while the person is offline`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        harness.writeToBot(alice, bot.bot.username, text = "/start")
        val sent = harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, "Вы офлайн")).body<SentMessage>()

        val queued = harness.services.messages.queued(harness.accountId(alice), 0, 10).single()
        assertEquals(bot.bot.username, queued.sender)
        assertEquals(BotMessage(sent.messageId, "Вы офлайн"), queued.botMessage())
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(alice.token) }) {
            assertEquals(queued, (nextFrame() as ServerFrame.Deliver).envelope)
        }
    }

    @Test
    fun `a bot cannot write to someone who never wrote to it`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val stranger = harness.register()
        val refused = harness.botPost(bot.token, "sendMessage", SendMessage(stranger.username, "Купите слона"))
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("chat_not_started", refused.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.NotFound, harness.botPost(bot.token, "sendMessage", SendMessage(randomUsername(), "кто здесь")).status)
        assertEquals(0, harness.services.messages.queued(harness.accountId(stranger), 0, 10).size)
    }

    @Test
    fun `texts and buttons are checked`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        harness.writeToBot(alice, bot.bot.username, text = "/start")
        suspend fun send(text: String = "ок", buttons: List<List<InlineButton>> = emptyList()) =
            harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, text, buttons)).body<ErrorBody>().code

        assertEquals("invalid_text", send(text = ""))
        assertEquals("invalid_text", send(text = "x".repeat(Bots.MAX_TEXT + 1)))
        val data = InlineButton("Да", data = "yes")
        assertEquals("invalid_buttons", send(buttons = List(Bots.MAX_BUTTON_ROWS + 1) { listOf(data) }))
        assertEquals("invalid_buttons", send(buttons = listOf(List(Bots.MAX_BUTTONS_IN_ROW + 1) { data })))
        assertEquals("invalid_buttons", send(buttons = listOf(emptyList())))
        assertEquals("invalid_buttons", send(buttons = listOf(listOf(InlineButton("Оба", data = "a", url = "https://example.com")))))
        assertEquals("invalid_buttons", send(buttons = listOf(listOf(InlineButton("Ничего")))))
        val longData = "x".repeat(Bots.MAX_CALLBACK_DATA + 1)
        assertEquals("invalid_buttons", send(buttons = listOf(listOf(InlineButton("Длинно", data = longData)))))
        assertEquals("invalid_buttons", send(buttons = listOf(listOf(InlineButton("Ссылка", url = "http://example.com")))))
        assertEquals("invalid_buttons", send(buttons = listOf(listOf(InlineButton("Ссылка", url = "javascript:alert(1)")))))
        assertEquals("invalid_buttons", send(buttons = listOf(listOf(InlineButton(" ", data = "x")))))

        val full = List(Bots.MAX_BUTTON_ROWS) { List(Bots.MAX_BUTTONS_IN_ROW) { data } }
        assertEquals(
            HttpStatusCode.OK,
            harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, "x".repeat(Bots.MAX_TEXT), full)).status,
        )
    }

    @Test
    fun `people send exactly one of text and button press`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        val both = harness.client.post("${Bots.userPath(bot.bot.username)}/updates") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(UserToBot("x", text = "a", callback = CallbackPress("m", "d")))
        }
        assertEquals(HttpStatusCode.BadRequest, both.status)
        assertEquals(HttpStatusCode.BadRequest, harness.writeToBot(alice, bot.bot.username).status)
        assertEquals("invalid_text", harness.writeToBot(alice, bot.bot.username, text = " ").body<ErrorBody>().code)
        assertEquals(
            "invalid_text",
            harness.writeToBot(alice, bot.bot.username, text = "x".repeat(Bots.MAX_TEXT + 1)).body<ErrorBody>().code,
        )
    }

    @Test
    fun `a bot edits its own messages only`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val other = harness.createBot(harness.register())
        val alice = harness.register()
        harness.writeToBot(alice, bot.bot.username, text = "/start")
        harness.writeToBot(alice, other.bot.username, text = "/start")
        val sent = harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, "Было")).body<SentMessage>()

        val buttons = listOf(listOf(InlineButton("Ещё", data = "more")))
        val edited = harness.botPost(bot.token, "editMessage", EditMessage(alice.username, sent.messageId, "Стало", buttons))
        assertEquals(sent, edited.body<SentMessage>())
        val last = harness.services.messages.queued(harness.accountId(alice), 0, 10).last().botMessage()
        assertEquals(BotMessage(sent.messageId, "Стало", buttons, editOf = sent.messageId), last)

        val foreign = harness.botPost(other.token, "editMessage", EditMessage(alice.username, sent.messageId, "Чужое"))
        assertEquals(HttpStatusCode.NotFound, foreign.status)
        assertEquals(HttpStatusCode.NotFound, harness.botPost(bot.token, "editMessage", EditMessage(alice.username, "nope", "?")).status)
    }

    @Test
    fun `a button press reaches the bot and its answer shows a notice`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        val bob = harness.register()
        harness.writeToBot(alice, bot.bot.username, text = "/start")
        harness.writeToBot(bob, bot.bot.username, text = "/start")
        val buttons = listOf(listOf(InlineButton("Да", data = "yes")))
        val sent = harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, "Согласны?", buttons)).body<SentMessage>()
        val offset = harness.getUpdates(bot.token).last().updateId + 1

        // Bob cannot press a button under a message sent to Alice.
        val yes = CallbackPress(sent.messageId, "yes")
        assertEquals(HttpStatusCode.NotFound, harness.writeToBot(bob, bot.bot.username, callback = yes).status)
        assertEquals(HttpStatusCode.NoContent, harness.writeToBot(alice, bot.bot.username, callback = yes).status)

        val press = harness.getUpdates(bot.token, offset).single().callback
        assertNotNull(press)
        assertEquals(alice.username, press!!.from)
        assertEquals(sent.messageId, press.messageId)
        assertEquals("yes", press.data)

        val aliceId = harness.accountId(alice)
        val before = harness.services.messages.queued(aliceId, 0, 10).size
        assertEquals(HttpStatusCode.NoContent, harness.botPost(bot.token, "answerCallback", AnswerCallback(press.id, "Принято")).status)
        val notice = harness.services.messages.queued(aliceId, 0, 10).last()
        assertEquals(before + 1, harness.services.messages.queued(aliceId, 0, 10).size)
        assertEquals(BotMessage(press.id, text = "", notice = "Принято"), notice.botMessage())

        // A second answer is ignored, an unknown one is not found.
        assertEquals(HttpStatusCode.NoContent, harness.botPost(bot.token, "answerCallback", AnswerCallback(press.id, "Снова")).status)
        assertEquals(before + 1, harness.services.messages.queued(aliceId, 0, 10).size)
        assertEquals(HttpStatusCode.NotFound, harness.botPost(bot.token, "answerCallback", AnswerCallback("nope")).status)
    }

    @Test
    fun `an answer without a notice sends nothing`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        harness.writeToBot(alice, bot.bot.username, text = "/start")
        val question = SendMessage(alice.username, "?", listOf(listOf(InlineButton("Да", data = "y"))))
        val sent = harness.botPost(bot.token, "sendMessage", question)
        harness.writeToBot(alice, bot.bot.username, callback = CallbackPress(sent.body<SentMessage>().messageId, "y"))
        val press = checkNotNull(harness.getUpdates(bot.token).last().callback)
        assertEquals(HttpStatusCode.NoContent, harness.botPost(bot.token, "answerCallback", AnswerCallback(press.id)).status)
        assertEquals(1, harness.services.messages.queued(harness.accountId(alice), 0, 10).size)
    }

    @Test
    fun `commands are checked and shown in the bot info`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        val commands = listOf(BotCommand("start", "Начать"), BotCommand("help_me", "Помощь"))
        assertEquals(HttpStatusCode.NoContent, harness.botPost(bot.token, "setCommands", SetCommands(commands)).status)
        val info = harness.client.get(Bots.userPath(bot.bot.username)) { bearerAuth(alice.token) }.body<BotInfo>()
        assertEquals(commands, info.commands)
        assertEquals(commands, harness.botGet(bot.token, "getMe").body<BotInfo>().commands)

        suspend fun rejected(commands: List<BotCommand>) =
            harness.botPost(bot.token, "setCommands", SetCommands(commands)).body<ErrorBody>().code == "invalid_commands"
        assertTrue(rejected(listOf(BotCommand("Start", "Начать"))))
        assertTrue(rejected(listOf(BotCommand("/start", "Начать"))))
        assertTrue(rejected(listOf(BotCommand("x".repeat(33), "Длинно"))))
        assertTrue(rejected(listOf(BotCommand("start", "x".repeat(257)))))
        assertTrue(rejected(listOf(BotCommand("start", "a"), BotCommand("start", "b"))))
        assertTrue(rejected(List(Bots.MAX_COMMANDS + 1) { BotCommand("c$it", "Команда") }))
        assertEquals(commands, harness.botGet(bot.token, "getMe").body<BotInfo>().commands)
    }

    @Test
    fun `a webhook receives updates with the secret and they leave the queue`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        val received = CompletableDeferred<Pair<String?, Update>>()
        val hook = webhookServer { secret, body ->
            received.complete(secret to BleskJson.decodeFromString(Update.serializer(), body))
            200
        }
        try {
            val url = "http://127.0.0.1:${hook.address.port}/hook"
            assertEquals(HttpStatusCode.NoContent, harness.botPost(bot.token, "setWebhook", SetWebhook(url, "s3cret-Value_1")).status)
            harness.writeToBot(alice, bot.bot.username, text = "через вебхук")

            val (secret, update) = withTimeout(10.seconds) { received.await() }
            assertEquals("s3cret-Value_1", secret)
            assertEquals("через вебхук", update.message?.text)
            assertEquals(alice.username, update.message?.from)
            eventually { assertEquals(emptyList<Update>(), harness.getUpdates(bot.token)) }
        } finally {
            hook.stop(0)
        }
    }

    @Test
    fun `a failing webhook leaves the update for getUpdates`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val alice = harness.register()
        val calls = AtomicInteger()
        val hook = webhookServer { secret, _ ->
            assertNull(secret)
            calls.incrementAndGet()
            500
        }
        try {
            harness.botPost(bot.token, "setWebhook", SetWebhook("http://127.0.0.1:${hook.address.port}/hook"))
            harness.writeToBot(alice, bot.bot.username, text = "не дошло")
            // Every attempt is made (two in tests), then the update simply stays queued.
            eventually { assertEquals(2, calls.get()) }
            delay(200)
            assertEquals("не дошло", harness.getUpdates(bot.token).single().message?.text)
        } finally {
            hook.stop(0)
        }
        assertEquals(HttpStatusCode.NoContent, harness.botPostEmpty(bot.token, "deleteWebhook").status)
    }

    @Test
    fun `webhooks must be https on a public address in production`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        assertEquals(
            "invalid_webhook",
            harness.botPost(bot.token, "setWebhook", SetWebhook("ftp://example.com/hook")).body<ErrorBody>().code,
        )
        assertEquals(
            "invalid_webhook",
            harness.botPost(bot.token, "setWebhook", SetWebhook("https://example.com/hook", "bad secret")).body<ErrorBody>().code,
        )

        val strict = WebhookPolicy(allowPrivateAddresses = false)
        assertFalse(strict.allows("http://example.com/hook"))
        assertFalse(strict.allows("https://127.0.0.1/hook"))
        assertFalse(strict.allows("https://localhost/hook"))
        assertFalse(strict.allows("https://10.1.2.3/hook"))
        assertFalse(strict.allows("https://[::1]/hook"))
        assertFalse(strict.allows("https://user:pass@1.1.1.1/hook"))
        assertTrue(strict.allows("https://1.1.1.1/hook"))
        listOf("192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "fd00::1", "fe80::1").forEach {
            assertFalse(it, WebhookPolicy.isPublic(InetAddress.getByName(it)))
        }
        assertTrue(WebhookPolicy.isPublic(InetAddress.getByName("8.8.8.8")))
    }

    private fun Envelope.botMessage(): BotMessage =
        BleskJson.decodeFromString(BotMessage.serializer(), Base64.getDecoder().decode(body).decodeToString())

    private fun webhookServer(handle: (secret: String?, body: String) -> Int): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/hook") { exchange ->
                val body = exchange.requestBody.readBytes().decodeToString()
                val status = handle(exchange.requestHeaders.getFirst(Bots.WEBHOOK_SECRET_HEADER), body)
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            start()
        }
}
