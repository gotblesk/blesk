package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.RoomRole
import app.blesk.shared.protocol.ServerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * What a group change queues goes out once the change is stored, on a database opened the way the phone opens it: a
 * file with a write-ahead log. There a read on another connection, such as the outbox's pass makes, does not see a
 * transaction until it is committed. In memory every read waits for the commit instead, so the other tests cannot
 * show a pass that started too early and found nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GroupUpdatesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = FakeServer()
    private val db = run {
        context.deleteDatabase(FILE)
        Room.databaseBuilder(context, BleskDatabase::class.java, FILE)
            // What Room's automatic mode picks on a phone.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .allowMainThreadQueries()
            .build()
    }
    private val store = SignalStore(db.signal())
    private val accounts = AccountManager(store, KeyManager(store))
    private val api = server.api()

    /** Every pass and retry timer of the outbox runs as a child of this. */
    private val outboxJobs = SupervisorJob()
    private val outbox = Outbox(
        db = db,
        api = api,
        crypto = MessageCrypto(store) { ME },
        accounts = accounts,
        me = ME,
        scope = CoroutineScope(outboxJobs + Dispatchers.Default),
        log = EventLog(),
        clock = System::currentTimeMillis,
        retryMillis = RETRY_MILLIS,
        shareProfile = {},
        linkOf = { null },
    )
    private val slowCommits = SlowCommits()
    private val engine =
        GroupEngine(db, GroupCrypto(store) { ME }, ME, slowCommits, System::currentTimeMillis, labels = { "" }, purged = {})
    private val peers = mutableListOf<BleskDatabase>()

    /**
     * The phone's outbox, except that while [on], queueing an item lets any pass it started end before the change goes
     * on: the commit at its latest, after such a pass has read the queue.
     */
    private inner class SlowCommits : GroupOutbox by outbox {
        @Volatile
        var on = false

        override suspend fun queue(recipient: String, content: Content) {
            outbox.queue(recipient, content)
            if (on) untilTheOutboxIsIdle()
        }
    }

    @Before
    fun setUp() = runBlocking {
        assertTrue(db.openHelper.writableDatabase.isWriteAheadLoggingEnabled)
        assertEquals(RegisterResult.Ok, accounts.register(api, ME))
        listOf(BOB, CAROL, DAVE).forEach { register(it) }
    }

    @After
    fun tearDown() {
        outboxJobs.cancel()
        db.close()
        peers.forEach { it.close() }
        context.deleteDatabase(FILE)
    }

    @Test
    fun theAnswerToAJoinRequestGoesOut() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf(BOB, CAROL))
        val code = engine.setInvite(group, enabled = true)!!
        untilTheOutboxIsIdle()
        val before = envelopesTo(BOB, CAROL, DAVE)

        slowCommits.on = true
        engine.onJoinRequest(DAVE, Body.JoinRequest(group, code))
        untilTheOutboxIsIdle()

        assertEquals(emptyList<String>(), db.outbox().all().map { it.recipient })
        assertEquals(before.map { it + 1 }, envelopesTo(BOB, CAROL, DAVE))
    }

    @Test
    fun theVoiceKeyReplacedAfterALeaveGoesOut() = runBlocking {
        val room = engine.create(GroupKind.Room, "Двор", listOf(BOB, CAROL))
        untilTheOutboxIsIdle()
        val before = envelopesTo(BOB)

        slowCommits.on = true
        engine.onLeave(CAROL, room)
        untilTheOutboxIsIdle()

        assertEquals(emptyList<String>(), db.outbox().all().map { it.recipient })
        assertEquals(before.map { it + 1 }, envelopesTo(BOB))
    }

    @Test
    fun aClosedChannelsKeyAndMessageReachOnlyItsReaders() = runBlocking {
        val room = engine.create(GroupKind.Room, "Клуб", listOf(BOB, CAROL))
        listOf(MODS, FANS).forEach { assertTrue(engine.change(room, GroupChange.SaveRole(RoomRole(it, it)))) }
        assertTrue(engine.change(room, GroupChange.AssignRole(BOB, MODS, on = true)))
        assertTrue(engine.change(room, GroupChange.AssignRole(CAROL, FANS, on = true)))
        assertTrue(engine.addChannel(room, "модерация", GroupChannelKind.Text, adminsOnly = false))
        val closed = engine.state(room)!!.channels.first { it.title == "модерация" }.id
        assertTrue(engine.change(room, GroupChange.HideChannel(closed, setOf(GroupPolicy.EVERYONE, FANS))))
        val conversation = GroupEngine.channelConversation(room, closed)
        untilTheOutboxIsIdle()

        val start = envelopesTo(BOB, CAROL)
        val startSealed = sealedTo(BOB, CAROL)
        assertTrue(engine.send(conversation, text("модераторам")))
        untilTheOutboxIsIdle()
        // Bob: our key for the channel, then the message.
        assertEquals(listOf(2, 0), envelopesTo(BOB, CAROL).since(start))
        assertEquals(listOf(1, 0), sealedTo(BOB, CAROL).since(startSealed))

        val revoked = envelopesTo(BOB, CAROL)
        val revokedSealed = sealedTo(BOB, CAROL)
        // One change takes the channel from Bob's role and gives it to Carol's.
        assertTrue(engine.change(room, GroupChange.HideChannel(closed, setOf(GroupPolicy.EVERYONE, MODS))))
        assertTrue(engine.send(conversation, text("теперь фанатам")))
        untilTheOutboxIsIdle()
        // Bob: only the new state. Carol: the new state, a new key for the channel, the message.
        assertEquals(listOf(1, 3), envelopesTo(BOB, CAROL).since(revoked))
        assertEquals(listOf(0, 1), sealedTo(BOB, CAROL).since(revokedSealed))
    }

    private fun text(value: String) = Content(UUID.randomUUID().toString(), System.currentTimeMillis(), Body.Text(value))

    private fun List<Int>.since(before: List<Int>) = zip(before) { now, then -> now - then }

    private suspend fun register(name: String) {
        val peer = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
        peers += peer
        val peerStore = SignalStore(peer.signal())
        assertEquals(RegisterResult.Ok, AccountManager(peerStore, KeyManager(peerStore)).register(server.api(), name))
    }

    private fun envelopesTo(vararg names: String): List<Int> =
        names.map { name -> server.transport(name).frames.replayCache.count { it is ServerFrame.Deliver } }

    private fun sealedTo(vararg names: String): List<Int> = names.map { name ->
        server.transport(name).frames.replayCache.count { it is ServerFrame.Deliver && it.envelope.type == EnvelopeType.SENDERKEY }
    }

    /** No pass is running or about to run. Nothing here is refused, so no retry timer is ever waiting either. */
    private suspend fun untilTheOutboxIsIdle() = withTimeout(TIMEOUT) {
        while (outboxJobs.children.any { it.isActive }) delay(POLL)
    }

    private companion object {
        const val FILE = "group-updates.db"
        const val ME = "alice"
        const val BOB = "bob"
        const val CAROL = "carol"
        const val DAVE = "dave"
        const val MODS = "mods"
        const val FANS = "fans"
        const val RETRY_MILLIS = 15_000L
        const val TIMEOUT = 60_000L
        const val POLL = 20L
    }
}
