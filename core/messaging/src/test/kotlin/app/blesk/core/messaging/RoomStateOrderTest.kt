package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.MAX_REFUSED_PER_SENDER
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.RoomRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * States of a room come one after another by the owner's count first, then by version, on every phone alike. What waits
 * for a state, a message written in it or a state built on it, waits by the same order; a room let go of comes back only
 * with a state after the one that put us out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RoomStateOrderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
    private val outbox = object : GroupOutbox {
        override suspend fun queue(recipient: String, content: Content) = Unit

        override suspend fun queueSealed(recipient: String, content: Content, type: Int, body: String) = Unit

        override fun flush(): Job = Job().apply { complete() }
    }
    private val engine = GroupEngine(
        db = db,
        crypto = GroupCrypto(SignalStore(db.signal())) { ME },
        me = ME,
        outbox = outbox,
        clock = ::clock,
        labels = { "" },
        purged = {},
        voiceKeys = VoiceKeys(db, ME, outbox, ::clock),
    )

    private fun clock() = System.currentTimeMillis()

    @After
    fun tearDown() = db.close()

    @Test
    fun aRemovalFarAheadSentToUsAloneLeavesTheRoomHere() = runBlocking {
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", room))
        // Carol may ban us. Taken, a state without us as far ahead as a version goes would keep every later one, Bob's
        // too, from bringing the room back, while every other phone still counts us in.
        val withoutUs = room.copy(version = Long.MAX_VALUE, members = room.members.filter { it.username != ME })
        assertNotEquals(UpdateOutcome.Taken, engine.onUpdate("carol", withoutUs))
        assertNotNull(engine.state(ROOM))
    }

    @Test
    fun aRoomTheOwnerPutUsOutOfStaysGoneWhateverAnAdminNumberedLater() = runBlocking {
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", room))
        // Bob removes us. Lena renamed a channel twice before she heard of it: every phone puts Bob's state after hers.
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", made(room, "bob", GroupChange.Remove(ME))))
        val once = made(room, "lena", GroupChange.RenameChannel(OPEN_ID, MAIN))
        val twice = made(once, "lena", GroupChange.RenameChannel(OPEN_ID, LOBBY))
        assertEquals(UpdateOutcome.Ignored, engine.onUpdate("lena", twice))
        assertNull(engine.state(ROOM))
    }

    @Test
    fun theOwnersStateWithUsBringsTheRoomBackAfterAnAdminsRemovalOfTheSameNumber() = runBlocking {
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", room))
        // Lena removes us while Bob renames the room: every phone puts Bob's state, where we are, after hers.
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("lena", made(room, "lena", GroupChange.Remove(ME))))
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", made(room, "bob", GroupChange.Describe(title = "Клуб 2"))))
        assertNotNull(engine.state(ROOM))
    }

    @Test
    fun aMessageWrittenInTheOwnersNextStateWaitsForItWhateverItsNumber() = runBlocking {
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", room))
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("lena", made(room, "lena", GroupChange.RenameChannel(OPEN_ID, MAIN))))
        // Bob let Yan in at the same moment. Yan's first message reaches us before Bob's state does.
        val joined = GroupPolicy.versioned(room, "bob", room.copy(members = room.members + GroupMember("yan", GroupRole.Member)))
        val open = GroupEngine.channelConversation(ROOM, OPEN_ID)
        val hello = Content(
            id = UUID.randomUUID().toString(),
            sentAtMillis = 1,
            body = Body.Text("привет"),
            group = open,
            groupVersion = joined.version,
            groupOwnerVersion = joined.ownerVersion,
        )
        assertEquals(Admission.Hold, engine.admission(engine.state(ROOM)!!, open, "yan", hello))
        engine.hold("yan", hello)

        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", joined))
        assertEquals(listOf(Released("yan", hello)), engine.released(ROOM))
    }

    @Test
    fun anAdminsStatesKeptWhileWeWereBehindAreTakenThoughTheFirstWentMissing() = runBlocking {
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", room))
        // Bob's next state reached Lena and not yet us. She renames a channel on it more times than this phone keeps
        // states of one sender: her first is let go of, and the next carries its line as well as its own.
        val bobs = made(room, "bob", GroupChange.Describe(description = "о книгах"))
        var lenas = bobs
        repeat(MAX_REFUSED_PER_SENDER + 1) { step ->
            lenas = made(lenas, "lena", GroupChange.RenameChannel(OPEN_ID, "канал $step"))
            assertEquals(UpdateOutcome.Refused, engine.onUpdate("lena", lenas))
        }

        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", bobs))
        assertEquals(lenas, engine.state(ROOM))
    }

    @Test
    fun membersRefusedStatesPushOutNoManagersState() = runBlocking {
        val socks = (1..SOCKS).map { GroupMember("sock$it", GroupRole.Member) }
        val crowded = room.copy(members = room.members + socks)
        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", crowded))
        val bobs = made(crowded, "bob", GroupChange.Describe(description = "о книгах"))
        val lenas = made(bobs, "lena", GroupChange.RenameChannel(OPEN_ID, MAIN))
        assertEquals(UpdateOutcome.Refused, engine.onUpdate("lena", lenas))
        // Members who manage nothing send states as far on in the owner's count as they like: kept, they push out no
        // state of one who manages the room.
        socks.forEach { sock ->
            repeat(MAX_REFUSED_PER_SENDER) { step ->
                val junk = crowded.copy(ownerVersion = JUNK_OWNER_VERSION, version = 2L + step, title = "Чужой")
                assertEquals(UpdateOutcome.Refused, engine.onUpdate(sock.username, junk))
            }
        }

        assertEquals(UpdateOutcome.Taken, engine.onUpdate("bob", bobs))
        assertEquals(lenas, engine.state(ROOM))
    }

    private fun made(state: GroupState, actor: String, change: GroupChange): GroupState =
        requireNotNull(GroupPolicy.apply(state, actor, change, clock()))

    /** Bob's room: Lena is an admin with every admin right; Carol's guards' role may ban and do nothing else. */
    private val room = GroupState(
        id = ROOM,
        kind = GroupKind.Room,
        title = "Клуб",
        version = 1,
        epoch = 0,
        members = listOf(
            GroupMember("bob", GroupRole.Owner),
            GroupMember(ME, GroupRole.Member),
            GroupMember("lena", GroupRole.Admin),
            GroupMember("carol", GroupRole.Member, roles = listOf(GUARDS)),
        ),
        channels = listOf(GroupChannel(OPEN_ID, "общее")),
        roles = listOf(RoomRole(GUARDS, "Охрана", rights = setOf(Right.Ban))),
    )

    private companion object {
        const val ME = "alice"
        const val ROOM = "7e1d2c3b-4a59-4687-b6c5-d4e3f2a1b0c9"
        const val OPEN_ID = "open"
        const val GUARDS = "guards"
        const val MAIN = "главное"
        const val LOBBY = "лобби"

        /** Accounts in the room that manage nothing; together they send more states than this phone keeps. */
        const val SOCKS = 8
        const val JUNK_OWNER_VERSION = 50L
    }
}
