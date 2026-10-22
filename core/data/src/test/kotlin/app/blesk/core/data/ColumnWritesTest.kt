package app.blesk.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ContactEntity
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.GroupEntity
import app.blesk.core.data.db.GroupSenderKeyEntity
import app.blesk.core.data.db.HeldGroupMessageEntity
import app.blesk.core.data.db.MAX_HELD_PER_GROUP
import app.blesk.core.data.db.MessageDao
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.db.VoiceKeyEntity
import app.blesk.core.data.model.SendStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Rows several writers touch at once: the sending coroutine and receipts on a message's status, you and their
 * profile on a contact, leaving a group and the send handing out our key. Each writer changes only its own
 * columns, so none can put back what another just wrote.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ColumnWritesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
    private val messages = db.messages()
    private val contacts = db.contacts()
    private val groups = db.groups()

    @After
    fun tearDown() = db.close()

    private fun message(id: String, author: String = ME, status: SendStatus = SendStatus.Sending) =
        MessageEntity(id, "bob", author, "привет", sentAt = 1, status = status.name)

    private suspend fun status(id: String) = messages.get(id)?.status

    @Test
    fun `a late Sent does not take back a receipt that came first`() = runBlocking {
        messages.insert(message("m"))
        // The sending coroutine has its answer from the server but has not written it yet; the receipt gets there first.
        messages.advanceStatus(listOf("m"), ME, SendStatus.Delivered)
        messages.advanceStatus(listOf("m"), ME, SendStatus.Sent)
        assertEquals(SendStatus.Delivered.name, status("m"))

        messages.advanceStatus(listOf("m"), ME, SendStatus.Read)
        messages.advanceStatus(listOf("m"), ME, SendStatus.Delivered)
        assertEquals(SendStatus.Read.name, status("m"))
    }

    @Test
    fun `only your own messages move, and a failure is set whatever came before`() = runBlocking {
        messages.insert(message("mine"))
        messages.insert(message("theirs", author = "bob", status = SendStatus.Delivered))
        messages.advanceStatus(listOf("mine", "theirs"), ME, SendStatus.Read)
        assertEquals(SendStatus.Read.name, status("mine"))
        assertEquals(SendStatus.Delivered.name, status("theirs"))

        messages.setStatus(listOf("mine"), ME, SendStatus.Failed.name)
        assertEquals(SendStatus.Failed.name, status("mine"))
        messages.advanceStatus(listOf("mine"), ME, SendStatus.Sent)
        assertEquals(SendStatus.Sent.name, status("mine"))
    }

    @Test
    fun `a receipt naming over a thousand messages moves them all, batch by batch`() = runBlocking {
        val ids = (1..1_200).map { "m$it" }
        ids.forEach { messages.insert(message(it)) }
        // Robolectric's SQLite takes far more values per statement than older phones do, so each one is counted here.
        val bound = mutableListOf<Int>()
        val counting = object : MessageDao by messages {
            override suspend fun advanceStatus(ids: List<String>, author: String, status: SendStatus) =
                super<MessageDao>.advanceStatus(ids, author, status)

            override suspend fun raiseStatus(ids: List<String>, author: String, status: String, below: List<String>) {
                bound += ids.size + below.size + 2
                messages.raiseStatus(ids, author, status, below)
            }
        }
        counting.advanceStatus(ids, ME, SendStatus.Delivered)
        assertTrue(ids.all { status(it) == SendStatus.Delivered.name })
        assertTrue(bound.isNotEmpty() && bound.all { it <= OLD_SQLITE_VALUES })
    }

    @Test
    fun `edits, reactions, files and cards leave the status alone`() = runBlocking {
        messages.insert(message("m", status = SendStatus.Sent))
        messages.advanceStatus(listOf("m"), ME, SendStatus.Delivered)
        messages.editText("m", ME, "привет!")
        messages.changeReactions("m") { """[{"symbol":"x","users":["bob"]}]""" }
        messages.setAttachment("m", "{}")
        messages.setLink("m", "{}")
        messages.clearRemote("m")
        val row = messages.get("m")!!
        assertEquals(SendStatus.Delivered.name, row.status)
        assertEquals("привет!", row.text)
        assertTrue(row.edited)
    }

    @Test
    fun `a public post sent again keeps the file downloaded or cleared meanwhile`() = runBlocking {
        val post = MessageEntity(
            "post:club:1", "space:club", "bob", "анонс", sentAt = 1, status = SendStatus.Read.name, attachment = "placeholder",
        )
        messages.insert(post)
        messages.setAttachment(post.id, "cleared")
        // What storing the same post again does: the insert keeps the row, then only the server's columns change.
        assertEquals(-1L, messages.insert(post))
        messages.setPost(post.id, "space:club:main", "анонс!", edited = true, replyToId = "post:club:0")

        val row = messages.get(post.id)!!
        assertEquals("cleared", row.attachment)
        assertEquals("space:club:main", row.conversationId)
        assertEquals("анонс!", row.text)
        assertTrue(row.edited)
        assertEquals("post:club:0", row.replyToId)
    }

    @Test
    fun `an edit by someone else than the author changes nothing`() = runBlocking {
        messages.insert(message("m"))
        assertEquals(0, messages.editText("m", "mallory", "подмена"))
        assertEquals("привет", messages.get("m")?.text)
    }

    @Test
    fun `nothing written after a deletion brings the message back`() = runBlocking {
        messages.insert(message("m"))
        messages.delete("m")
        messages.advanceStatus(listOf("m"), ME, SendStatus.Sent)
        messages.setStatus(listOf("m"), ME, SendStatus.Failed.name)
        messages.editText("m", ME, "снова")
        messages.changeReactions("m") { "[]" }
        messages.setAttachment("m", "{}")
        messages.setLink("m", "{}")
        assertNull(messages.get("m"))
    }

    @Test
    fun `receipts and the sending coroutine racing on many messages always end at the furthest status`() = runBlocking {
        val ids = (1..200).map { "m$it" }
        ids.forEach { messages.insert(message(it)) }
        val writes = ids.flatMap { id -> listOf(SendStatus.Read, SendStatus.Sent, SendStatus.Delivered).map { id to it } }.shuffled()
        writes.map { (id, status) -> async(Dispatchers.IO) { messages.advanceStatus(listOf(id), ME, status) } }.awaitAll()
        assertEquals(listOf(SendStatus.Read.name), ids.map { status(it) }.distinct())
    }

    @Test
    fun `reactions arriving together are all kept`() = runBlocking {
        messages.insert(message("m"))
        val users = (1..50).map { "user$it" }
        users.map { user -> async(Dispatchers.IO) { messages.changeReactions("m") { "$it|$user" } } }.awaitAll()
        assertEquals(users.toSet(), messages.get("m")!!.reactions.split('|').drop(1).toSet())
    }

    @Test
    fun `their profile keeps the block and the add you made meanwhile`() = runBlocking {
        contacts.insertIfAbsent(ContactEntity("alice", "alice", identityKey = "key", added = false))
        contacts.setBlocked("alice", true)
        contacts.markAdded("alice")
        contacts.setVerified("alice", true)

        // What a profile, a message or a group from Alice does when it arrives.
        assertEquals(-1L, contacts.insertIfAbsent(ContactEntity("alice", "alice", added = false)))
        contacts.setProfile("alice", "Алиса", "о себе", "https://blesk.example", photo = "photo.jpg")

        val alice = contacts.get("alice")!!
        assertTrue(alice.blocked)
        assertTrue(alice.added)
        assertTrue(alice.verified)
        assertEquals("key", alice.identityKey)
        assertEquals("Алиса", alice.name)
        assertEquals("photo.jpg", alice.photo)
    }

    @Test
    fun `opening a chat with someone who just wrote adds them and keeps their profile`() = runBlocking {
        contacts.insertIfAbsent(ContactEntity("alice", "alice", added = false))
        contacts.setProfile("alice", "Алиса", "", "", photo = null)
        contacts.markAddedWithKey("alice", "key")
        contacts.markAddedWithKey("alice", "other")

        val alice = contacts.get("alice")!!
        assertTrue(alice.added)
        assertEquals("key", alice.identityKey)
        assertEquals("Алиса", alice.name)
    }

    @Test
    fun `a changed key takes the mark off, and checking it again clears the warning`() = runBlocking {
        contacts.insertIfAbsent(ContactEntity("alice", "alice"))
        contacts.setBlocked("alice", true)
        contacts.setVerified("alice", true)
        assertEquals(1, contacts.markKeyChanged("alice"))
        contacts.get("alice")!!.let { assertTrue(it.keyChanged && !it.verified && it.blocked) }
        contacts.setVerified("alice", false)
        assertTrue(contacts.get("alice")!!.keyChanged)
        contacts.setVerified("alice", true)
        contacts.get("alice")!!.let { assertTrue(!it.keyChanged && it.verified && it.blocked) }
        assertEquals(0, contacts.markKeyChanged("nobody"))
    }

    @Test
    fun `a new title or a second create leaves the rest of the chat as it was`() = runBlocking {
        val conversations = db.conversations()
        val fresh = ConversationEntity("alice", "Direct", "alice", "EndToEnd", "alice", updatedAt = 1)
        conversations.insertIfAbsent(fresh)
        conversations.setPreview("alice", null, "привет", 2)
        conversations.incrementUnread("alice")
        conversations.setDraft("alice", "черновик")

        assertEquals(-1L, conversations.insertIfAbsent(fresh))
        conversations.setTitle("alice", "Алиса")

        val row = conversations.get("alice")!!
        assertEquals("Алиса", row.title)
        assertEquals("привет", row.previewText)
        assertEquals(1, row.unread)
        assertEquals("черновик", row.draft)
    }

    @Test
    fun `a sender key written after leaving a group does not bring it back`() = runBlocking {
        groups.insertIfAbsent(GroupEntity("g", "state"))
        groups.putSenderKey(GroupSenderKeyEntity("g", "", "key-1", epoch = 0, holders = "[\"bob\"]"))
        groups.putSenderKey(GroupSenderKeyEntity("g", "", "key-1", epoch = 0, holders = "[\"bob\",\"carol\"]"))
        assertEquals("[\"bob\",\"carol\"]", groups.senderKey("g", "")?.holders)

        groups.delete("g")
        groups.deleteSenderKeys("g")
        assertEquals(0, groups.setState("g", "later"))
        groups.putSenderKey(GroupSenderKeyEntity("g", "c", "key-2", epoch = 0))
        assertNull(groups.get("g"))
        assertNull(groups.senderKey("g", ""))
        assertNull(groups.senderKey("g", "c"))
    }

    @Test
    fun `held messages are capped at 200 per group and the oldest go first`() = runBlocking {
        val over = MAX_HELD_PER_GROUP + 5
        (1..over).forEach { groups.hold(held("g", version = 1, content = "m$it")) }
        groups.hold(held("h", version = 1, content = "other"))

        assertEquals((6..over).map { "m$it" }, groups.releasable("g", version = 1).map { it.content })
        assertEquals(listOf("other"), groups.releasable("h", version = 1).map { it.content })
    }

    @Test
    fun `releasable takes only rows at or below the version, in order, and removes them`() = runBlocking {
        listOf(3, 1, 2, 5).forEach { groups.hold(held("g", version = it, content = "v$it", at = it.toLong())) }
        groups.hold(held("h", version = 1, content = "other", at = 10))

        assertEquals(listOf("v1", "v2"), groups.releasable("g", version = 2).map { it.content })
        assertEquals(emptyList<String>(), groups.releasable("g", version = 2).map { it.content })
        groups.dropHeldBefore(at = 4)
        assertEquals(listOf("v5"), groups.releasable("g", version = 5).map { it.content })
        groups.deleteHeld("h")
        assertEquals(emptyList<String>(), groups.releasable("h", version = 1).map { it.content })
    }

    @Test
    fun `channels with messages are found by the room prefix only, each once`() = runBlocking {
        listOf("g", "g:x", "g:x", "g2:x", "gg:x", "h:x").forEachIndexed { index, conversation ->
            messages.insert(MessageEntity("m$index", conversation, "bob", "привет", sentAt = 1, status = SendStatus.Read.name))
        }
        assertEquals(listOf("g:x"), messages.channelsWithMessages("g"))
    }

    @Test
    fun `a voice key given by an admin is kept only if newer, and only while its group is here`() = runBlocking {
        assertFalse(groups.offerVoiceKey(voiceKey(version = 1, key = "k1"), KEEPER))
        groups.insertIfAbsent(GroupEntity("g", "state"))
        assertTrue(groups.offerVoiceKey(voiceKey(version = 2, key = "k2"), KEEPER))
        assertFalse(groups.offerVoiceKey(voiceKey(version = 1, key = "k1"), KEEPER))
        assertFalse(groups.offerVoiceKey(voiceKey(version = 2, key = "k2-again"), KEEPER))
        assertEquals("k2", groups.voiceKey("g", "c")?.key)
        assertTrue(groups.offerVoiceKey(voiceKey(version = 3, key = "k3"), KEEPER))
        assertEquals("k3", groups.voiceKey("g", "c")?.key)

        groups.delete("g")
        groups.deleteVoiceKeys("g")
        assertFalse(groups.offerVoiceKey(voiceKey(version = 4, key = "k4"), KEEPER))
        assertNull(groups.voiceKey("g", "c"))
    }

    @Test
    fun `the keeper's voice key takes the place of another admin's, however new that one says it is`() = runBlocking {
        groups.insertIfAbsent(GroupEntity("g", "state"))
        assertTrue(groups.offerVoiceKey(voiceKey(version = Long.MAX_VALUE, key = "erin's", giver = "erin"), KEEPER))
        assertTrue(groups.offerVoiceKey(voiceKey(version = 5, key = "k5"), KEEPER))
        assertEquals("k5" to KEEPER, groups.voiceKey("g", "c")?.let { it.key to it.giver })
        // The keeper's own keys still only move forward.
        assertFalse(groups.offerVoiceKey(voiceKey(version = 4, key = "k4"), KEEPER))
        assertFalse(groups.offerVoiceKey(voiceKey(version = 3, key = "erin's again", giver = "erin"), KEEPER))
        assertEquals("k5", groups.voiceKey("g", "c")?.key)
    }

    private fun voiceKey(version: Long, key: String, giver: String = KEEPER) =
        VoiceKeyEntity("g", "c", version, room = "voice-g-c-$version", key = key, epoch = 0, giver = giver)

    private fun held(group: String, version: Int, content: String, at: Long = 1) =
        HeldGroupMessageEntity(groupId = group, sender = "bob", content = content, version = version, heldAt = at)

    private companion object {
        const val ME = "me"
        const val KEEPER = "bob"

        /** SQLite's limit on values bound to one statement before 3.32, as on older phones. */
        const val OLD_SQLITE_VALUES = 999
    }
}
