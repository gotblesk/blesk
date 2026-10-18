package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.OutboxEntity
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * One phone's groups, changed from several sides at once: its own admin actions and sends, and what other members
 * send. Whatever one of them does to the group must still stand once another, started earlier, is done.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GroupEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
    private val outbox = HeldOutbox()
    private val crypto = GroupCrypto(SignalStore(db.signal())) { ME }
    private val engine = GroupEngine(db, crypto, ME, outbox, ::clock)

    /** While set, reading the clock waits for it: the moment a change has read the group and not yet written it. */
    @Volatile
    private var clockHeld: CompletableDeferred<Unit>? = null
    private val clockWaits = AtomicInteger()

    private fun clock(): Long {
        clockHeld?.let { gate ->
            clockWaits.incrementAndGet()
            runBlocking { gate.await() }
        }
        return System.currentTimeMillis()
    }

    /**
     * Updates go into the phone's real outbox table, so a change cut short shows what it left there; group messages
     * only note whom they are for. While [keyOut] is set, handing out a sender key waits for it; an update to
     * [updateHeldFor] waits for good.
     */
    private inner class HeldOutbox : GroupOutbox {
        val sealedTo = CopyOnWriteArrayList<String>()
        val keysHeld = AtomicInteger()
        val updatesHeld = AtomicInteger()

        @Volatile
        var keyOut: CompletableDeferred<Unit>? = null

        @Volatile
        var updateHeldFor: String? = null

        override suspend fun queue(recipient: String, content: Content) {
            when (content.body) {
                is Body.SenderKey -> keyOut?.let { gate ->
                    keysHeld.incrementAndGet()
                    gate.await()
                }
                is Body.GroupUpdate -> {
                    if (recipient == updateHeldFor) {
                        updatesHeld.incrementAndGet()
                        CompletableDeferred<Unit>().await()
                    }
                    val json = storageJson.encodeToString(Content.serializer(), content)
                    db.outbox().insert(OutboxEntity(recipient = recipient, content = json, createdAt = content.sentAtMillis))
                }
                else -> Unit
            }
        }

        override suspend fun queueSealed(recipient: String, content: Content, type: Int, body: String) {
            sealedTo += recipient
        }

        override fun flush(): Job = Job().apply { complete() }
    }

    @After
    fun tearDown() = db.close()

    private fun text(value: String) = Content(UUID.randomUUID().toString(), 1, Body.Text(value))

    @Test
    fun aMemberWhoLeftWhileOurKeyWentOutStaysOut() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf("bob", "carol"))
        val keyOut = CompletableDeferred<Unit>().also { outbox.keyOut = it }
        val sending = launch(Dispatchers.IO) { engine.send(group, text("всем")) }
        untilAKeyIsHeld()
        // Carol leaves while our key is on its way: the group moves to a new epoch without her.
        engine.onLeave("carol", group)
        keyOut.complete(Unit)
        sending.join()

        val state = engine.state(group)!!
        assertEquals(listOf(ME, "bob"), state.members.map { it.username })
        assertEquals(1, state.epoch)
        // The send that was under way had her in it; the next one does not.
        outbox.keyOut = null
        outbox.sealedTo.clear()
        engine.send(group, text("без Кэрол"))
        assertEquals(listOf("bob"), outbox.sealedTo)
    }

    @Test
    fun aGroupLeftWhileOurKeyWentOutStaysGone() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf("bob"))
        val keyOut = CompletableDeferred<Unit>().also { outbox.keyOut = it }
        val sending = launch(Dispatchers.IO) { engine.send(group, text("всем")) }
        untilAKeyIsHeld()
        engine.leave(group)
        keyOut.complete(Unit)
        sending.join()

        assertNull(engine.state(group))
        assertNull(db.conversations().get(group))
    }

    @Test
    fun aLeaveDuringARemovalIsNotUndoneByIt() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf("bob", "carol", "dave"))
        val held = CompletableDeferred<Unit>().also { clockHeld = it }
        val removing = launch(Dispatchers.IO) { engine.remove(group, "dave") }
        until { clockWaits.get() > 0 }
        // Carol's leave arrives while the removal has read the group and not yet written it.
        val leaving = launch(Dispatchers.IO) { engine.onLeave("carol", group) }
        // Not a guess at how long anything takes: the leave cannot pass a removal under way, however long it waits.
        // Done in a moment otherwise, this only gives it the time to get in between, the way the old engine let it.
        withTimeoutOrNull(GRACE) { leaving.join() }
        held.complete(Unit)
        joinAll(removing, leaving)

        val state = engine.state(group)!!
        assertEquals(listOf(ME, "bob"), state.members.map { it.username })
        assertEquals(2, state.epoch)
        engine.send(group, text("без Кэрол и Дейва"))
        assertEquals(listOf("bob"), outbox.sealedTo)
    }

    @Test
    fun aChangeCutShortLeavesNoHalfOfIt() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf("bob", "carol"))
        db.outbox().all().forEach { db.outbox().delete(it.id) }
        outbox.updateHeldFor = "carol"
        // The screen that renamed the group is closed while the new state is being queued for its members.
        val renaming = launch(Dispatchers.IO) { engine.rename(group, "Будни") }
        until { outbox.updatesHeld.get() > 0 }
        renaming.cancelAndJoin()

        assertEquals("Выходные", engine.state(group)!!.title)
        assertEquals(emptyList<String>(), db.outbox().all().map { it.recipient })
    }

    @Test
    fun aGroupWeLeftStaysGoneWhateverTheAdminSentBeforeHearingOfIt() = runBlocking {
        val bob = GroupMember("bob", GroupRole.Owner)
        val me = GroupMember(ME, GroupRole.Member)
        engine.onUpdate("bob", stateOf(1, bob, me))
        engine.leave(GROUP)
        // Bob renamed the group before our leave reached him.
        engine.onUpdate("bob", stateOf(2, bob, me, title = "Будни"))
        assertNull(engine.state(GROUP))
        assertNull(db.conversations().get(GROUP))

        // Only asking to join ourselves lets his answer bring it back.
        engine.joining(GROUP)
        engine.onUpdate("bob", stateOf(3, bob, me, title = "Будни"))
        assertEquals("Будни", engine.state(GROUP)?.title)
        assertEquals("Будни", db.conversations().get(GROUP)?.title)
    }

    @Test
    fun aGroupWeWereRemovedFromComesBackOnlyByALaterState() = runBlocking {
        val bob = GroupMember("bob", GroupRole.Owner)
        val carol = GroupMember("carol", GroupRole.Admin)
        val me = GroupMember(ME, GroupRole.Member)
        engine.onUpdate("bob", stateOf(1, bob, carol, me))
        // Carol removes us, while a change Bob made at the same time, with us still in, is on its way.
        engine.onUpdate("carol", stateOf(2, bob, carol))
        engine.onUpdate("bob", stateOf(2, bob, carol, me, title = "Будни"))
        assertNull(engine.state(GROUP))
        assertNull(db.conversations().get(GROUP))

        // Added again afterwards: a state newer than the one that put us out.
        engine.onUpdate("bob", stateOf(3, bob, carol, me))
        assertEquals(3, engine.state(GROUP)?.version)
    }

    private fun stateOf(version: Int, vararg members: GroupMember, title: String = "Выходные") =
        GroupState(id = GROUP, kind = GroupKind.Group, title = title, version = version, epoch = 0, members = members.toList())

    private suspend fun untilAKeyIsHeld() = until { outbox.keysHeld.get() > 0 }

    private suspend fun until(condition: () -> Boolean) = withTimeout(TIMEOUT) {
        while (!condition()) delay(POLL)
    }

    private companion object {
        const val ME = "alice"
        const val GROUP = "g"
        const val TIMEOUT = 60_000L
        const val POLL = 20L
        const val GRACE = 500L
    }
}
