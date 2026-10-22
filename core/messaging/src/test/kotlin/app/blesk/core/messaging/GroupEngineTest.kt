package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.HeldGroupMessageEntity
import app.blesk.core.data.db.LeftGroupEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.db.OutboxEntity
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.SendStatus
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.RoomRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.protocol.message.SenderKeyMessage
import java.util.Base64
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
    private val purged = CopyOnWriteArrayList<MessageEntity>()
    private val engine =
        GroupEngine(db, crypto, ME, outbox, ::clock, labels = { FILE_LABEL }, purged = { purged += it })

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
     * Updates go into the phone's real outbox table, so a change cut short shows what it left there; sender keys and
     * group messages only note whom they are for and which key they are, by its distribution id. While [keyOut] is
     * set, handing out a sender key waits for it; an update to [updateHeldFor] waits for good. [afterUpdate] runs once
     * an update is queued.
     */
    private inner class HeldOutbox : GroupOutbox {
        val sealedTo = CopyOnWriteArrayList<String>()
        val sealed = CopyOnWriteArrayList<Pair<String, UUID>>()
        val keysTo = CopyOnWriteArrayList<Pair<String, UUID>>()
        val keysHeld = AtomicInteger()
        val updatesHeld = AtomicInteger()
        val flushes = AtomicInteger()

        @Volatile
        var afterUpdate: () -> Unit = {}

        @Volatile
        var keyOut: CompletableDeferred<Unit>? = null

        @Volatile
        var updateHeldFor: String? = null

        override suspend fun queue(recipient: String, content: Content) {
            when (val body = content.body) {
                is Body.SenderKey -> {
                    keyOut?.let { gate ->
                        keysHeld.incrementAndGet()
                        gate.await()
                    }
                    keysTo += recipient to SenderKeyDistributionMessage(Base64.getDecoder().decode(body.distribution)).distributionId
                }
                is Body.GroupUpdate -> {
                    if (recipient == updateHeldFor) {
                        updatesHeld.incrementAndGet()
                        CompletableDeferred<Unit>().await()
                    }
                    val json = storageJson.encodeToString(Content.serializer(), content)
                    db.outbox().insert(OutboxEntity(recipient = recipient, content = json, createdAt = content.sentAtMillis))
                    afterUpdate()
                }
                else -> Unit
            }
        }

        override suspend fun queueSealed(recipient: String, content: Content, type: Int, body: String) {
            sealedTo += recipient
            sealed += recipient to SenderKeyMessage(Base64.getDecoder().decode(body)).distributionId
        }

        fun forgetSent() {
            sealedTo.clear()
            sealed.clear()
            keysTo.clear()
        }

        override fun flush(): Job {
            flushes.incrementAndGet()
            return Job().apply { complete() }
        }
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
        assertNull(db.groups().senderKey(group, ""))
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
    fun aChangeStoredAsItsScreenClosesIsStillSent() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf("bob"))
        val flushes = outbox.flushes.get()
        lateinit var renaming: Job
        // The screen is closed once the update to Bob is queued: the change is committed, the screen never hears of it.
        outbox.afterUpdate = { renaming.cancel() }
        renaming = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { engine.rename(group, "Будни") }
        renaming.join()
        outbox.afterUpdate = {}

        assertTrue(renaming.isCancelled)
        assertEquals("Будни", engine.state(group)!!.title)
        assertEquals(listOf("bob", "bob"), db.outbox().all().map { it.recipient })
        assertEquals(flushes + 1, outbox.flushes.get())
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

    @Test
    fun aClosedChannelGoesOnlyToThoseWhoSeeIt() = runBlocking {
        val club = club()
        assertTrue(engine.send(club.closed, text("модераторам")))

        assertEquals(listOf("bob"), outbox.keysTo.map { it.first })
        assertEquals(outbox.keysTo.toList(), outbox.sealed.toList())
    }

    @Test
    fun anOpenChannelStillGoesToEveryone() = runBlocking {
        val club = club()
        engine.send(club.closed, text("модераторам"))
        val closedKey = outbox.sealed.single().second
        outbox.forgetSent()
        engine.send(club.open, text("всем"))

        assertEquals(listOf("bob", "carol"), outbox.sealed.map { it.first })
        val roomKey = outbox.sealed.map { it.second }.distinct().single()
        assertNotEquals(closedKey, roomKey)
    }

    @Test
    fun losingAClosedChannelGivesItANewKey() = runBlocking {
        val club = club()
        engine.send(club.closed, text("до"))
        val before = outbox.sealed.single().second
        engine.change(club.id, GroupChange.AssignRole("bob", MODS, on = false))
        engine.change(club.id, GroupChange.AssignRole("carol", MODS, on = true))
        outbox.forgetSent()
        engine.send(club.closed, text("после"))

        val after = outbox.sealed.single()
        assertEquals("carol", after.first)
        assertNotEquals(before, after.second)
        assertEquals(listOf(after), outbox.keysTo.toList())
    }

    @Test
    fun gainingAClosedChannelHandsOverTheSameKey() = runBlocking {
        val club = club()
        engine.send(club.closed, text("до"))
        val key = outbox.sealed.single().second
        engine.change(club.id, GroupChange.AssignRole("carol", MODS, on = true))
        outbox.forgetSent()
        engine.send(club.closed, text("и Кэрол"))

        assertEquals(listOf("carol" to key), outbox.keysTo.toList())
        assertEquals(listOf("bob" to key, "carol" to key), outbox.sealed.toList())
    }

    @Test
    fun aNewEpochGivesEveryScopeANewKey() = runBlocking {
        val club = club(listOf("bob", "carol", "dave"))
        engine.send(club.open, text("всем"))
        engine.send(club.closed, text("модераторам"))
        val before = outbox.sealed.map { it.second }.toSet()
        engine.remove(club.id, "dave")
        outbox.forgetSent()
        engine.send(club.open, text("без Дейва"))
        engine.send(club.closed, text("модераторам без Дейва"))

        val after = outbox.sealed.map { it.second }.toSet()
        assertEquals(2, before.size)
        assertEquals(2, after.size)
        assertEquals(emptySet<UUID>(), before intersect after)
        assertEquals(listOf("bob", "carol", "bob"), outbox.keysTo.map { it.first })
    }

    @Test
    fun aRevertedEpochNeverReusesAKey() = runBlocking {
        val bob = GroupMember("bob", GroupRole.Owner)
        val me = GroupMember(ME, GroupRole.Member)
        // Someone left, then an admin who had not heard of it sent a newer state of the epoch before.
        val keys = listOf(1 to 0, 2 to 1, 3 to 0).map { (version, epoch) ->
            engine.onUpdate("bob", stateOf(version, bob, me, epoch = epoch))
            outbox.forgetSent()
            engine.send(GROUP, text("версия $version"))
            outbox.sealed.single().second
        }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun aChannelOnlyWeReadSendsNothing() = runBlocking {
        val club = club()
        engine.change(club.id, GroupChange.AssignRole("bob", MODS, on = false))
        assertTrue(engine.send(club.closed, text("себе")))
        assertEquals(emptyList<Pair<String, UUID>>(), outbox.keysTo + outbox.sealed)
    }

    @Test
    fun aGroupOfOneSendsNothing() = runBlocking {
        val group = engine.create(GroupKind.Group, "Заметки", emptyList())
        assertTrue(engine.send(group, text("себе")))
        assertEquals(emptyList<Pair<String, UUID>>(), outbox.keysTo + outbox.sealed)
    }

    @Test
    fun aReopenedChannelUsesTheRoomKey() = runBlocking {
        val club = club()
        engine.send(club.open, text("всем"))
        val roomKey = outbox.sealed.first().second
        engine.send(club.closed, text("модераторам"))
        engine.change(club.id, GroupChange.HideChannel(club.closedChannel, emptySet()))
        outbox.forgetSent()
        engine.send(club.closed, text("теперь всем"))

        assertEquals(listOf("bob" to roomKey, "carol" to roomKey), outbox.sealed.toList())
        assertEquals(emptyList<Pair<String, UUID>>(), outbox.keysTo.toList())
    }

    @Test
    fun aChannelClosedAgainWithOtherReadersGetsANewKey() = runBlocking {
        val club = club()
        engine.send(club.closed, text("модераторам"))
        val closedKey = outbox.sealed.single().second
        engine.change(club.id, GroupChange.HideChannel(club.closedChannel, emptySet()))
        engine.change(club.id, GroupChange.AssignRole("bob", MODS, on = false))
        engine.change(club.id, GroupChange.AssignRole("carol", MODS, on = true))
        engine.change(club.id, GroupChange.HideChannel(club.closedChannel, setOf(GroupPolicy.EVERYONE)))
        outbox.forgetSent()
        engine.send(club.closed, text("снова закрыт"))

        val sent = outbox.sealed.single()
        assertEquals("carol", sent.first)
        assertNotEquals(closedKey, sent.second)
        assertEquals(listOf(sent), outbox.keysTo.toList())
    }

    @Test
    fun concurrentSendsShareOneKey() = runBlocking {
        val club = club()
        val keyOut = CompletableDeferred<Unit>().also { outbox.keyOut = it }
        val first = launch(Dispatchers.IO) { engine.send(club.open, text("раз")) }
        untilAKeyIsHeld()
        val second = launch(Dispatchers.IO) { engine.send(club.open, text("два")) }
        // Not a guess at how long anything takes: the second send cannot reach the key while the first hands it out.
        // This only gives it the time to, the way it could before sends took turns.
        withTimeoutOrNull(GRACE) { until { outbox.keysHeld.get() > 1 } }
        keyOut.complete(Unit)
        joinAll(first, second)

        assertEquals(listOf("bob", "carol"), outbox.keysTo.map { it.first })
        assertEquals(4, outbox.sealed.size)
        assertEquals(outbox.keysTo.map { it.second }.distinct(), outbox.sealed.map { it.second }.distinct())
        assertEquals(1, outbox.sealed.map { it.second }.distinct().size)
    }

    @Test
    fun aChannelClosedWhileOurKeyWentOutIsClosedForTheNextMessage() = runBlocking {
        val club = club()
        val keyOut = CompletableDeferred<Unit>().also { outbox.keyOut = it }
        val sending = launch(Dispatchers.IO) { engine.send(club.open, text("всем")) }
        untilAKeyIsHeld()
        // The channel is closed to all but the moderators while our key for the whole room is on its way to Carol too.
        engine.change(club.id, GroupChange.HideChannel(club.openChannel, setOf(GroupPolicy.EVERYONE)))
        keyOut.complete(Unit)
        sending.join()
        val roomKey = outbox.sealed.first().second
        outbox.keyOut = null
        outbox.forgetSent()
        engine.send(club.open, text("модераторам"))

        val sent = outbox.sealed.single()
        assertEquals("bob", sent.first)
        assertNotEquals(roomKey, sent.second)
        assertEquals(listOf(sent), outbox.keysTo.toList())
    }

    @Test
    fun writingToAnUnknownChannelFails() = runBlocking {
        val club = club()
        assertFalse(engine.send(GroupEngine.channelConversation(club.id, "zzz"), text("в никуда")))
        assertEquals(emptyList<Pair<String, UUID>>(), outbox.keysTo + outbox.sealed)
    }

    @Test
    fun addingAChannelNeedsTheChannelsRight() = runBlocking {
        val guards = RoomRole("guards", "Охрана", rights = setOf(Right.Ban))
        val room = GroupState(
            id = GROUP,
            kind = GroupKind.Room,
            title = "Клуб",
            version = 1,
            epoch = 0,
            members = listOf(GroupMember("bob", GroupRole.Owner), GroupMember(ME, GroupRole.Member, roles = listOf(guards.id))),
            channels = listOf(GroupChannel("c1", "общее"), GroupChannel("c2", "второй")),
            roles = listOf(guards),
        )
        engine.onUpdate("bob", room)
        assertFalse(engine.addChannel(GROUP, "новый", GroupChannelKind.Text, adminsOnly = false))
        assertFalse(engine.removeChannel(GROUP, "c2"))
        assertEquals(room.channels, engine.state(GROUP)?.channels)

        engine.onUpdate("bob", room.copy(version = 2, roles = listOf(guards.copy(rights = guards.rights + Right.Channels))))
        assertTrue(engine.addChannel(GROUP, "новый", GroupChannelKind.Text, adminsOnly = false))
        assertTrue(engine.removeChannel(GROUP, "c2"))
        assertEquals(listOf("общее", "новый"), engine.state(GROUP)?.channels?.map { it.title })
    }

    @Test
    fun admissionStoresHoldsAndDrops() {
        val closed = GroupEngine.channelConversation(ROOM, CLOSED_ID)
        // Dave sees the channel, Carol does not; ours is the state at version 5.
        val cases = listOf(true, false).flatMap { weSee ->
            listOf("dave", "carol").flatMap { sender -> listOf(4, 5, 6).map { version -> Triple(weSee, sender, version) } }
        }
        for ((weSee, sender, version) in cases) {
            val state = roomOf(version = 5, ourRoles = if (weSee) listOf(MODS) else emptyList())
            val expected = when {
                weSee && sender == "dave" -> Admission.Store
                version > state.version -> Admission.Hold
                else -> Admission.Drop
            }
            val content = text("модераторам").copy(group = closed, groupVersion = version)
            assertEquals("we see: $weSee, from $sender at $version", expected, engine.admission(state, closed, sender, content))
        }

        val state = roomOf(version = 5)
        // Only an admin writes in the news; whoever reads them may react there.
        val news = GroupEngine.channelConversation(ROOM, NEWS_ID)
        assertEquals(Admission.Drop, engine.admission(state, news, "carol", text("можно и мне?")))
        assertEquals(Admission.Store, engine.admission(state, news, "bob", text("новость")))
        assertEquals(Admission.Store, engine.admission(state, news, "carol", reaction()))
        // A channel our state does not have: perhaps one made in a state we are yet to get.
        val unknown = GroupEngine.channelConversation(ROOM, "zzz")
        assertEquals(Admission.Drop, engine.admission(state, unknown, "dave", text("в никуда")))
        assertEquals(Admission.Hold, engine.admission(state, unknown, "dave", text("в новый").copy(groupVersion = 6)))
    }

    @Test
    fun aHeldMessageComesBackOnceItsStateArrives() = runBlocking {
        engine.onUpdate("bob", roomOf(version = 1, ourRoles = emptyList()))
        val closed = GroupEngine.channelConversation(ROOM, CLOSED_ID)
        // Dave wrote it once Bob had let us in; Bob's state saying so is still on its way.
        val early = text("теперь и тебе").copy(group = closed, groupVersion = 2)
        assertEquals(Admission.Hold, engine.admission(engine.state(ROOM)!!, closed, "dave", early))
        engine.hold("dave", early)
        assertEquals(emptyList<Released>(), engine.released(ROOM))

        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", roomOf(version = 2)))
        assertEquals(listOf(Released("dave", early)), engine.released(ROOM))
        assertEquals(emptyList<Released>(), engine.released(ROOM))
        assertEquals(Admission.Store, engine.admission(engine.state(ROOM)!!, closed, "dave", early))
    }

    @Test
    fun aStateThatOpensAChannelFromAManagerWithoutTheRightIsRefused() = runBlocking {
        val guards = RoomRole("guards", "Охрана", rights = setOf(Right.Ban))
        val room = roomOf(version = 1, ourRoles = emptyList()).let { state ->
            state.copy(
                roles = state.roles + guards,
                members = state.members.map { if (it.username == "carol") it.copy(roles = listOf(guards.id)) else it },
            )
        }
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", room))
        val closed = GroupEngine.channelConversation(ROOM, CLOSED_ID)
        val opened = room.copy(version = 2, channels = room.channels.map { it.copy(hiddenFrom = emptySet()) })

        // Carol may ban, so her states are taken, but not one that opens a channel: that needs the channels right.
        assertEquals(UpdateOutcome.Refused, engine.onUpdate("carol", opened))
        assertEquals(room.version, engine.state(ROOM)?.version)
        assertEquals(room.channels, engine.state(ROOM)?.channels)
        assertNull(db.conversations().get(closed))

        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", opened))
        assertNotNull(db.conversations().get(closed))
    }

    @Test
    fun losingAChannelTakesItsMessagesAndFilesOffThePhone() = runBlocking {
        engine.onUpdate("bob", roomOf(version = 1))
        val open = GroupEngine.channelConversation(ROOM, OPEN_ID)
        val closed = GroupEngine.channelConversation(ROOM, CLOSED_ID)
        val photo = stored(open, "", at = 1, attachment = PLAN)
        val plan = stored(closed, "тайный план", at = 2, attachment = PLAN)
        val note = stored(closed, "тайная заметка", at = 3)
        // As an incoming message leaves it: the room's line in the chat list shows the channel's last one.
        db.conversations().setPreview(ROOM, "dave", "тайная заметка", 3)
        assertEquals(2, db.messages().searchFullText("тайн*").size)

        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", roomOf(version = 2, ourRoles = emptyList())))

        assertNull(db.conversations().get(closed))
        assertNull(db.messages().get(plan.id))
        assertNull(db.messages().get(note.id))
        assertEquals(emptyList<MessageEntity>(), db.messages().searchFullText("тайн*"))
        assertEquals(listOf(plan.id), purged.map { it.id })
        assertNotNull(db.messages().get(photo.id))
        val room = db.conversations().get(ROOM)!!
        assertEquals("dave" to FILE_LABEL, room.previewAuthor to room.previewText)
    }

    @Test
    fun tidyRemovesLeftoversOfChannelsWeDoNotSee() = runBlocking {
        engine.onUpdate("bob", roomOf(version = 3, ourRoles = emptyList()))
        val open = GroupEngine.channelConversation(ROOM, OPEN_ID)
        val greeting = stored(open, "привет всем", at = 1)
        // What a build that stored whatever it could decrypt left behind, and what a crash left of a group we left.
        val hidden = stored(GroupEngine.channelConversation(ROOM, CLOSED_ID), "тайный план", at = 2, attachment = PLAN)
        val unknown = stored(GroupEngine.channelConversation(ROOM, "zzz"), "в никуда", at = 3)
        db.groups().markLeft(LeftGroupEntity(GROUP, version = 1, byUs = true))
        val left = stored(GROUP, "из прошлого", at = 4)
        // Held for a state that has come since, and for one that never came.
        val waited = text("дождался").copy(group = open, groupVersion = 3)
        engine.hold("dave", waited)
        val stale = storageJson.encodeToString(Content.serializer(), text("забыт").copy(group = open, groupVersion = 9))
        db.groups().hold(HeldGroupMessageEntity(groupId = ROOM, sender = "dave", content = stale, version = 9, heldAt = 0))

        assertEquals(listOf(Released("dave", waited)), engine.tidy())
        assertNotNull(db.messages().get(greeting.id))
        listOf(hidden, unknown, left).forEach { assertNull(db.messages().get(it.id)) }
        assertEquals(listOf(hidden.id), purged.map { it.id })

        assertEquals(emptyList<Released>(), engine.tidy())
        assertNotNull(db.messages().get(greeting.id))
        assertEquals(listOf(hidden.id), purged.map { it.id })
        // Too old to wait for its state: when it comes, nothing is left to give back.
        engine.onUpdate("bob", roomOf(version = 9, ourRoles = emptyList()))
        assertEquals(emptyList<Released>(), engine.released(ROOM))
    }

    @Test
    fun leavingAGroupTakesItsFilesToo() = runBlocking {
        val group = engine.create(GroupKind.Group, "Выходные", listOf("bob"))
        val file = stored(group, "", at = 1, attachment = PLAN)
        val words = stored(group, "до встречи", at = 2)
        engine.leave(group)

        assertNull(db.messages().get(file.id))
        assertNull(db.messages().get(words.id))
        assertEquals(listOf(file.id), purged.map { it.id })
    }

    @Test
    fun aGroupIdThatNamesAChatOfOursTakesNothingOffThePhone() = runBlocking {
        val chat = stored("dave", "привет", at = 1, attachment = PLAN)
        // Anyone may ask to join any group; this asks for one named like our chat with Dave.
        engine.onJoinRequest("mallory", Body.JoinRequest("dave", "code"))
        val notOurs = stateOf(1, GroupMember("mallory", GroupRole.Owner)).copy(id = "dave")
        assertEquals(UpdateOutcome.Ignored, engine.onUpdate("mallory", notOurs))

        assertNotNull(db.messages().get(chat.id))
        assertEquals(emptyList<MessageEntity>(), purged.toList())
    }

    /** A room of ours: Bob holds the moderators' role, the only one that sees [closed]; Carol has no role. */
    private class Club(val id: String, val openChannel: String, val closedChannel: String) {
        val open = GroupEngine.channelConversation(id, openChannel)
        val closed = GroupEngine.channelConversation(id, closedChannel)
    }

    private suspend fun club(others: List<String> = listOf("bob", "carol")): Club {
        val id = engine.create(GroupKind.Room, "Клуб", others)
        assertTrue(engine.change(id, GroupChange.SaveRole(RoomRole(MODS, "Модераторы"))))
        assertTrue(engine.addChannel(id, CLOSED, GroupChannelKind.Text, adminsOnly = false))
        val channels = engine.state(id)!!.channels
        val closed = channels.first { it.title == CLOSED }.id
        assertTrue(engine.change(id, GroupChange.HideChannel(closed, setOf(GroupPolicy.EVERYONE))))
        assertTrue(engine.change(id, GroupChange.AssignRole("bob", MODS, on = true)))
        return Club(id, channels.first { it.title == GroupEngine.DEFAULT_TEXT_CHANNEL }.id, closed)
    }

    private fun stateOf(version: Int, vararg members: GroupMember, title: String = "Выходные", epoch: Int = 0) =
        GroupState(id = GROUP, kind = GroupKind.Group, title = title, version = version, epoch = epoch, members = members.toList())

    /**
     * Bob's room, where we are a member holding [ourRoles]. Dave holds the moderators' role, the only one that sees
     * «модерация»; Carol has none; only admins write in «новости».
     */
    private fun roomOf(version: Int, ourRoles: List<String> = listOf(MODS)) = GroupState(
        id = ROOM,
        kind = GroupKind.Room,
        title = "Клуб",
        version = version,
        epoch = 0,
        members = listOf(
            GroupMember("bob", GroupRole.Owner),
            GroupMember(ME, GroupRole.Member, roles = ourRoles),
            GroupMember("dave", GroupRole.Member, roles = listOf(MODS)),
            GroupMember("carol", GroupRole.Member),
        ),
        channels = listOf(
            GroupChannel(OPEN_ID, "общее"),
            GroupChannel(CLOSED_ID, CLOSED, hiddenFrom = setOf(GroupPolicy.EVERYONE)),
            GroupChannel(NEWS_ID, "новости", adminsOnly = true),
        ),
        roles = listOf(RoomRole(MODS, "Модераторы")),
    )

    private fun reaction() = Content(UUID.randomUUID().toString(), 1, Body.Reaction("m1", "+"))

    /** A message as an earlier receive left it on the phone, from Dave. */
    private suspend fun stored(conversation: String, text: String, at: Long, attachment: Attachment? = null): MessageEntity {
        val row = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversation,
            authorId = "dave",
            text = text,
            sentAt = at,
            status = SendStatus.Delivered.name,
            attachment = encodeAttachment(attachment),
        )
        db.messages().insert(row)
        return row
    }

    private suspend fun untilAKeyIsHeld() = until { outbox.keysHeld.get() > 0 }

    private suspend fun until(condition: () -> Boolean) = withTimeout(TIMEOUT) {
        while (!condition()) delay(POLL)
    }

    private companion object {
        const val ME = "alice"
        const val GROUP = "g"
        const val ROOM = "r"
        const val OPEN_ID = "open"
        const val CLOSED_ID = "closed"
        const val NEWS_ID = "news"
        const val MODS = "mods"
        const val CLOSED = "модерация"
        const val FILE_LABEL = "Файл"
        const val TIMEOUT = 60_000L
        const val POLL = 20L
        const val GRACE = 500L
        val PLAN = Attachment.File("/media/plan.pdf", "plan.pdf", 1, "application/pdf")
    }
}
