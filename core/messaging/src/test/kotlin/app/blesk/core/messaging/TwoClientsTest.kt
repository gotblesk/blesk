package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.calls.CallSignal
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.InlineButton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Two real clients (database, libsignal, outbox, receipts) talking through an in-memory server.
 * Everything between them is ciphertext; this checks the plumbing around it end to end.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TwoClientsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = FakeServer()

    private inner class Client(val name: String, displayName: String) {
        val db: BleskDatabase = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
        val store = SignalStore(db.signal())
        val keys = KeyManager(store)
        val accounts = AccountManager(store, keys)
        val api = server.api()
        val repo = ServerChatRepository(
            db = db,
            api = api,
            socket = server.transport(name),
            crypto = MessageCrypto(store) { name },
            groupCrypto = GroupCrypto(store) { name },
            keys = keys,
            accounts = accounts,
            media = MediaVault(context),
            me = name,
            scope = scope,
            privacy = { Privacy() },
            profile = { displayName to "" },
            labels = { it?.javaClass?.simpleName.orEmpty() },
            log = EventLog(),
        )

        fun register() = runBlocking { assertEquals(RegisterResult.Ok, accounts.register(api, name)) }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeout(TIMEOUT) {
            var value: T? = null
            while (value == null) {
                value = block()
                if (value == null) kotlinx.coroutines.delay(50)
            }
            value
        }
    }

    private fun Client.messages(conversation: String): List<Message> = runBlocking { repo.messages(conversation).first() }

    @Test
    fun conversationEndToEnd() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.register()
        bob.register()
        alice.repo.start()
        bob.repo.start()

        assertEquals("bob", runBlocking { alice.repo.openDirect("bob") })
        alice.repo.send("bob", "привет")

        val received = eventually { bob.messages("alice").firstOrNull() }
        assertEquals("привет", received.text)
        eventually { bob.repo.contact("alice")?.takeIf { it.name == "Алиса" } }
        eventually { alice.messages("bob").firstOrNull()?.takeIf { it.status == SendStatus.Delivered } }

        bob.repo.markRead("alice")
        eventually { alice.messages("bob").firstOrNull()?.takeIf { it.status == SendStatus.Read } }

        bob.repo.toggleReaction("alice", received.id, "🔥")
        eventually { alice.messages("bob").first().reactions.firstOrNull()?.takeIf { it.symbol == "🔥" } }

        val sent = alice.messages("bob").first()
        alice.repo.edit("bob", sent.id, "привет!")
        eventually { bob.messages("alice").firstOrNull()?.takeIf { it.edited && it.text == "привет!" } }

        alice.repo.delete("bob", sent.id)
        eventually { bob.messages("alice").takeIf { it.isEmpty() } }

        val file = File(context.cacheDir, "notes.txt").apply { writeText("заметки") }
        alice.repo.sendAttachment("bob", Attachment.File(file.path, "notes.txt", file.length(), "text/plain"))
        val delivered = eventually {
            (bob.messages("alice").firstOrNull()?.attachment as? Attachment.File)?.takeIf { it.path.isNotEmpty() }
        }
        assertEquals("заметки", File(delivered.path).readText())
        assertTrue(server.transport("bob").acked.isNotEmpty())
    }

    @Test
    fun groupsAndRooms() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        listOf(alice, bob, carol).forEach {
            it.register()
            it.repo.start()
        }
        runBlocking {
            alice.repo.openDirect("bob")
            alice.repo.openDirect("carol")
        }
        val group = runBlocking { alice.repo.createGroup(GroupKind.Group, "Выходные", listOf("bob", "carol")) }
        eventually { runBlocking { bob.repo.group(group).first() } }
        eventually { runBlocking { carol.repo.group(group).first() } }

        alice.repo.send(group, "всем привет")
        assertEquals("всем привет", eventually { bob.messages(group).firstOrNull() }.text)
        assertEquals("всем привет", eventually { carol.messages(group).firstOrNull() }.text)

        bob.repo.send(group, "привет от Боба")
        eventually { alice.messages(group).firstOrNull { it.text == "привет от Боба" } }
        eventually { carol.messages(group).firstOrNull { it.text == "привет от Боба" } }

        runBlocking { assertTrue(alice.repo.removeMember(group, "carol")) }
        eventually { runBlocking { carol.repo.group(group).first() }.let { if (it == null) Unit else null } }
        alice.repo.send(group, "без Кэрол")
        eventually { bob.messages(group).firstOrNull { it.text == "без Кэрол" } }

        val room = runBlocking { alice.repo.createGroup(GroupKind.Room, "Студия", listOf("bob", "carol")) }
        val channel = eventually { runBlocking { bob.repo.conversation(room).first() }?.channels?.firstOrNull() }
        alice.repo.send(channel.id, "в общий канал")
        assertEquals("в общий канал", eventually { bob.messages(channel.id).firstOrNull() }.text)

        // Voice channels share one media key, replaced when someone is removed.
        val voiceKey = runBlocking { alice.repo.voiceKey(room) }
        assertNotNull(voiceKey)
        assertEquals(voiceKey, eventually { runBlocking { bob.repo.voiceKey(room) } })
        eventually { runBlocking { carol.repo.voiceKey(room) } }
        runBlocking { assertTrue(alice.repo.removeMember(room, "carol")) }
        val rotated = eventually { runBlocking { bob.repo.voiceKey(room) }?.takeIf { it != voiceKey } }
        assertEquals(runBlocking { alice.repo.voiceKey(room) }, rotated)
    }

    @Test
    fun callSignalsArriveEncrypted() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        listOf(alice, bob).forEach {
            it.register()
            it.repo.start()
        }
        val received = CompletableDeferred<CallSignal>()
        val subscribed = CompletableDeferred<Unit>()
        val listener = CoroutineScope(Dispatchers.Default).launch {
            received.complete(bob.repo.callSignals.onSubscription { subscribed.complete(Unit) }.first())
        }
        runBlocking { subscribed.await() }
        runBlocking { alice.repo.sendCallSignal("bob", Body.CallOffer("call-1", video = true, key = "media-key")) }
        val signal = runBlocking { withTimeout(TIMEOUT) { received.await() } }
        listener.cancel()
        assertEquals("alice", signal.from)
        assertEquals(Body.CallOffer("call-1", video = true, key = "media-key"), signal.body)
    }

    @Test
    fun timerForwardAndDraft() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        listOf(alice, bob, carol).forEach {
            it.register()
            it.repo.start()
        }
        runBlocking { alice.repo.openDirect("bob") }
        alice.repo.send("bob", "привет")
        eventually { bob.messages("alice").firstOrNull() }

        alice.repo.setTimer("bob", 3_600)
        eventually { runBlocking { bob.repo.conversation("alice").first() }?.takeIf { it.timerSeconds == 3_600 } }
        alice.repo.send("bob", "исчезнет")
        val vanishing = eventually { bob.messages("alice").firstOrNull { it.text == "исчезнет" } }
        assertNotNull(vanishing.expiresAt)
        assertNotNull(eventually { alice.messages("bob").firstOrNull { it.text == "исчезнет" } }.expiresAt)

        runBlocking { bob.repo.openDirect("carol") }
        bob.repo.forward("alice", vanishing.id, listOf("carol"))
        val forwarded = eventually { carol.messages("bob").firstOrNull { it.text == "исчезнет" } }
        assertNotNull(forwarded.forwardedFrom)

        alice.repo.saveDraft("bob", "недописанное")
        eventually { runBlocking { alice.repo.conversation("bob").first() }?.takeIf { it.draft == "недописанное" } }
    }

    @Test
    fun botChat() {
        val alice = Client("alice", "Алиса")
        alice.register()
        alice.repo.start()
        server.addBot(BotInfo("echo_bot", "Эхо", commands = listOf(BotCommand("start", "начать"))))

        val chat = runBlocking { alice.repo.openDirect("echo_bot") }
        assertEquals("echo_bot", chat)
        val conversation = runBlocking { alice.repo.conversation("echo_bot").first() }
        assertEquals(ConversationKind.Bot, conversation?.kind)
        assertEquals(Protection.Open, conversation?.protection)
        assertEquals(listOf("start"), runBlocking { alice.repo.commands("echo_bot") }.map { it.command })

        alice.repo.send("echo_bot", "/start")
        eventually { server.botInbox.firstOrNull { it.second.text == "/start" } }
        eventually { alice.messages("echo_bot").firstOrNull { it.status == SendStatus.Sent } }

        server.botSays("echo_bot", "alice", BotMessage("m1", "Выбери", listOf(listOf(InlineButton("Да", data = "yes")))))
        val withButtons = eventually { alice.messages("echo_bot").firstOrNull { it.text == "Выбери" } }
        assertEquals("yes", withButtons.buttons.single().single().data)

        alice.repo.press("echo_bot", withButtons.id, "yes")
        val press = eventually { server.botInbox.firstNotNullOfOrNull { it.second.callback } }
        assertEquals("m1", press.messageId)

        server.botSays("echo_bot", "alice", BotMessage("m2", "Выбрано", editOf = "m1"))
        eventually { alice.messages("echo_bot").firstOrNull { it.text == "Выбрано" && it.edited } }
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
