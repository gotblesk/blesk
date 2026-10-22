package app.blesk.integration

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
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.IncomingNote
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import app.blesk.core.messaging.AccountManager
import app.blesk.core.messaging.OwnProfile
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerChatRepository
import app.blesk.core.network.BleskApi
import app.blesk.core.network.ServerAddress
import app.blesk.core.network.Socket
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.RoomRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * Four people on the real server, the image the VPS runs with its PostgreSQL behind Caddy, reached
 * over TLS with the pinned key of a server without a domain: they register by invite, write, send a
 * file, talk in a group and in a room's closed channel, and call. Unlike TwoClientsTest, nothing here
 * is simulated between the clients.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RealServerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val address = ServerAddress(env("BLESK_IT_URL"), env("BLESK_IT_PIN"))

    // Names are new on every run, so the test also passes against a server that has seen it before.
    private val run = Random.nextLong(0, RUN_IDS).toString(RUN_RADIX)

    private inner class Client(base: String, displayName: String) {
        val name = "${base}_$run"

        /** Every code the client has put in its journal, in order. */
        val journal = CopyOnWriteArrayList<EventCode>()
        val db: BleskDatabase = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
        val store = SignalStore(db.signal())
        val keys = KeyManager(store)
        val accounts = AccountManager(store, keys)
        val api = BleskApi(address)
        val repo = ServerChatRepository(
            db = db,
            api = api,
            socket = Socket(api, scope),
            crypto = MessageCrypto(store) { name },
            groupCrypto = GroupCrypto(store) { name },
            keys = keys,
            accounts = accounts,
            media = MediaVault(context),
            me = name,
            scope = scope,
            privacy = { Privacy() },
            profile = { OwnProfile(displayName, "", emptyList(), null) },
            labels = { it?.javaClass?.simpleName.orEmpty() },
            log = EventLog(sink = { journal += it.code }),
        )

        fun join(invite: String) {
            runBlocking { assertEquals(RegisterResult.Ok, accounts.register(api, name, invite = invite)) }
            repo.start()
        }

        // Registration left the client signed in. Signing in again for each invite would draw on the per-address
        // budget that sign-ins, registrations and invites share, and four people joining at once would go over it.
        fun invite(): String = runBlocking { api.createInvite().code }

        fun messages(conversation: String): List<Message> = runBlocking { repo.messages(conversation).first() }

        fun state(group: String): GroupState = runBlocking { repo.group(group).first() }!!

        fun caughtUpWith(other: Client, group: String) {
            val version = other.state(group).version
            eventually { runBlocking { repo.group(group).first() }?.takeIf { it.version >= version } }
        }

        /** Group messages that reached this phone and were not kept: unreadable, refused or waiting for a state. */
        fun turnedAway(): Int = journal.count { it in TURNED_AWAY }

        /** What this client announces from now on, gathered as it comes. */
        fun notes(): List<IncomingNote> {
            val notes = CopyOnWriteArrayList<IncomingNote>()
            val subscribed = CompletableDeferred<Unit>()
            scope.launch { repo.incoming.onSubscription { subscribed.complete(Unit) }.collect { notes += it } }
            runBlocking { subscribed.await() }
            return notes
        }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun peopleWriteSendFilesTalkInGroupsAndCallThroughTheServer() {
        val alice = Client("alice", "Алиса")
        alice.join(env("BLESK_IT_INVITES").split(',').first())
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val dave = Client("dave", "Дэйв")
        listOf(bob, carol, dave).forEach { it.join(alice.invite()) }
        val used = runBlocking { alice.api.listInvites() }.invites.mapNotNull { it.usedBy }
        assertEquals(setOf(bob.name, carol.name, dave.name), used.toSet())

        converse(alice, bob)
        sendFile(alice, bob)
        talkInGroup(alice, bob, carol)
        closedChannel(alice, bob, carol, dave)
        call(alice, bob)
    }

    private fun converse(alice: Client, bob: Client) {
        runBlocking {
            alice.repo.openDirect(bob.name)
            bob.repo.openDirect(alice.name)
        }
        alice.repo.send(bob.name, "привет")
        val received = eventually { bob.messages(alice.name).firstOrNull { it.text == "привет" } }
        eventually { bob.repo.contact(alice.name)?.takeIf { it.name == "Алиса" } }
        eventually { alice.messages(bob.name).firstOrNull()?.takeIf { it.status >= SendStatus.Delivered } }

        bob.repo.markRead(alice.name)
        eventually { alice.messages(bob.name).firstOrNull()?.takeIf { it.status == SendStatus.Read } }

        bob.repo.toggleReaction(alice.name, received.id, "🔥")
        eventually { alice.messages(bob.name).first().reactions.firstOrNull()?.takeIf { it.symbol == "🔥" } }

        val sent = alice.messages(bob.name).first()
        alice.repo.edit(bob.name, sent.id, "привет!")
        eventually { bob.messages(alice.name).firstOrNull()?.takeIf { it.edited && it.text == "привет!" } }
        alice.repo.delete(bob.name, sent.id)
        eventually { bob.messages(alice.name).takeIf { it.isEmpty() } }

        bob.repo.send(alice.name, "ответ")
        eventually { alice.messages(bob.name).firstOrNull { it.text == "ответ" } }
    }

    private fun sendFile(alice: Client, bob: Client) {
        // Bigger than one upload chunk, so the file goes up and comes down in parts.
        val bytes = Random.nextBytes(FILE_BYTES)
        val file = File(context.cacheDir, "notes-$run.bin").apply { writeBytes(bytes) }
        alice.repo.sendAttachment(bob.name, Attachment.File(file.path, "notes.bin", file.length(), "application/octet-stream"))
        val delivered = eventually {
            bob.messages(alice.name).firstNotNullOfOrNull { message ->
                (message.attachment as? Attachment.File)?.takeIf { it.name == "notes.bin" && it.path.isNotEmpty() }
            }
        }
        assertArrayEquals(bytes, File(delivered.path).readBytes())
    }

    private fun talkInGroup(alice: Client, bob: Client, carol: Client) {
        runBlocking { alice.repo.openDirect(carol.name) }
        val group = runBlocking { alice.repo.createGroup(GroupKind.Group, "Выходные", listOf(bob.name, carol.name)) }
        eventually { runBlocking { bob.repo.group(group).first() } }
        eventually { runBlocking { carol.repo.group(group).first() } }

        alice.repo.send(group, "всем привет")
        eventually { bob.messages(group).firstOrNull { it.text == "всем привет" } }
        eventually { carol.messages(group).firstOrNull { it.text == "всем привет" } }
        bob.repo.send(group, "привет от Боба")
        eventually { alice.messages(group).firstOrNull { it.text == "привет от Боба" } }
        eventually { carol.messages(group).firstOrNull { it.text == "привет от Боба" } }

        runBlocking { assertTrue(alice.repo.removeMember(group, carol.name)) }
        eventually { runBlocking { carol.repo.group(group).first() }.let { if (it == null) Unit else null } }
        alice.repo.send(group, "без Кэрол")
        eventually { bob.messages(group).firstOrNull { it.text == "без Кэрол" } }
    }

    /**
     * A room channel hidden from everyone but the moderators, Bob and Dave. Carol is in the room and gets
     * nothing of it, not even a ciphertext she cannot open; once Bob is no longer a moderator, the channel
     * is gone from his phone and its next message reaches Dave alone.
     */
    private fun closedChannel(alice: Client, bob: Client, carol: Client, dave: Client) {
        runBlocking { alice.repo.openDirect(dave.name) }
        val others = listOf(bob, carol, dave)
        val room = runBlocking { alice.repo.createGroup(GroupKind.Room, "Клуб", others.map { it.name }) }
        runBlocking {
            assertTrue(alice.repo.change(room, GroupChange.SaveRole(RoomRole(MODERATORS, "Модераторы"))))
            assertTrue(alice.repo.addChannel(room, CLOSED, GroupChannelKind.Text, adminsOnly = false))
            val id = alice.state(room).channels.first { it.title == CLOSED }.id
            assertTrue(alice.repo.change(room, GroupChange.HideChannel(id, setOf(GroupPolicy.EVERYONE))))
            listOf(bob, dave).forEach { assertTrue(alice.repo.change(room, GroupChange.AssignRole(it.name, MODERATORS, on = true))) }
        }
        others.forEach { it.caughtUpWith(alice, room) }
        val channels = runBlocking { alice.repo.conversation(room).first() }!!.channels.filter { it.kind == RoomChannelKind.Text }
        val closed = channels.first { it.title == CLOSED }.id
        val general = channels.first { it.title != CLOSED }.id
        val notes = carol.notes()
        val turnedAway = carol.turnedAway()

        alice.repo.send(closed, "тайный план")
        eventually { bob.messages(closed).firstOrNull { it.text == "тайный план" } }
        eventually { dave.messages(closed).firstOrNull { it.text == "тайный план" } }
        bob.repo.send(closed, "тайный ответ")
        eventually { alice.messages(closed).firstOrNull { it.text == "тайный ответ" } }
        eventually { dave.messages(closed).firstOrNull { it.text == "тайный ответ" } }
        // Each outbox and the server keep their order, so whatever Alice and Bob sent Carol before these, she has handled.
        alice.repo.send(general, "всем привет")
        bob.repo.send(general, "и всем")
        eventually { carol.messages(general).firstOrNull { it.text == "всем привет" } }
        eventually { carol.messages(general).firstOrNull { it.text == "и всем" } }
        assertTrue(carol.messages(closed).isEmpty())
        assertNull(runBlocking { carol.db.conversations().get(closed) })
        assertTrue(runBlocking { carol.repo.search("тайный") }.isEmpty())
        assertEquals(emptyList<IncomingNote>(), notes.toList())
        assertEquals(turnedAway, carol.turnedAway())

        runBlocking { assertTrue(alice.repo.change(room, GroupChange.AssignRole(bob.name, MODERATORS, on = false))) }
        others.forEach { it.caughtUpWith(alice, room) }
        eventually { bob.messages(closed).takeIf { it.isEmpty() } }
        assertNull(runBlocking { bob.db.conversations().get(closed) })
        assertTrue(runBlocking { bob.repo.search("тайный") }.isEmpty())
        val bobTurnedAway = bob.turnedAway()

        alice.repo.send(closed, "тайный план Б")
        eventually { dave.messages(closed).firstOrNull { it.text == "тайный план Б" } }
        alice.repo.send(general, "снова всем")
        eventually { bob.messages(general).firstOrNull { it.text == "снова всем" } }
        eventually { carol.messages(general).firstOrNull { it.text == "снова всем" } }
        assertTrue(bob.messages(closed).isEmpty())
        assertTrue(runBlocking { bob.repo.search("тайный") }.isEmpty())
        assertEquals(bobTurnedAway, bob.turnedAway())
        assertTrue(carol.messages(closed).isEmpty())
        assertEquals(turnedAway, carol.turnedAway())
    }

    private fun call(alice: Client, bob: Client) {
        val received = CompletableDeferred<CallSignal>()
        val subscribed = CompletableDeferred<Unit>()
        val listener = scope.launch {
            received.complete(bob.repo.callSignals.onSubscription { subscribed.complete(Unit) }.first())
        }
        runBlocking { subscribed.await() }
        val offer = Body.CallOffer("call-$run", video = true, key = "media-key")
        runBlocking { alice.repo.sendCallSignal(bob.name, offer) }
        val signal = runBlocking { withTimeout(TIMEOUT) { received.await() } }
        listener.cancel()
        assertEquals(alice.name, signal.from)
        assertEquals(offer, signal.body)

        val token = runBlocking { alice.repo.callToken("call-$run") }
        assertTrue("the server gives no LiveKit token", token != null && token.token.isNotBlank())
    }

    /** Waits for [block] to give a value; on timeout the failure points at the waiting line of the test. */
    private fun <T> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeoutOrNull(TIMEOUT) {
            var value: T? = null
            while (value == null) {
                value = block()
                if (value == null) delay(POLL_MILLIS)
            }
            value
        }
    } ?: throw AssertionError("nothing after ${TIMEOUT / 1000} s")

    private companion object {
        const val TIMEOUT = 60_000L
        const val POLL_MILLIS = 100L
        const val FILE_BYTES = Api.CHUNK_BYTES + 123_456
        const val RUN_IDS = 36L * 36 * 36 * 36 * 36 * 36
        const val RUN_RADIX = 36
        const val MODERATORS = "mods"
        const val CLOSED = "модерация"
        val TURNED_AWAY = setOf(EventCode.DecryptFailed, EventCode.GroupMessageDropped, EventCode.GroupMessageHeld)

        fun env(name: String): String = System.getenv(name) ?: error("$name is not set: run scripts/ci/integration.sh")
    }
}
