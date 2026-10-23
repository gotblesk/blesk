package app.blesk.navigation

import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Scenarios 37 and 41: the official channel shows in «Чаты» without taking the newcomer screen away, and
 * without making notifications needed; a friend or a request does.
 */
class HomeNewcomerTest {
    private val saved = chat("saved", ConversationKind.Saved)
    private val official = chat("space:blesk", ConversationKind.Channel).copy(official = true, protection = Protection.Open)
    private val anya = chat("anya", ConversationKind.Direct)

    @Test
    fun theOfficialChannelAloneKeepsTheNewcomerScreen() {
        val home = chatsHome(listOf(saved, official), archived = emptyList(), demo = false)

        assertTrue(home.alone)
        assertEquals(official, home.official)
        assertEquals(emptyList<Conversation>(), home.friends)
    }

    @Test
    fun aFriendEndsTheNewcomerScreen() {
        val home = chatsHome(listOf(saved, official, anya), archived = emptyList(), demo = false)

        assertFalse(home.alone)
        assertEquals(listOf(anya), home.friends)
    }

    @Test
    fun aRequestEndsTheNewcomerScreen() {
        val stranger = chat("vera", ConversationKind.Direct).copy(request = true)

        val home = chatsHome(listOf(official, stranger), archived = emptyList(), demo = false)

        assertFalse(home.alone)
        assertEquals(listOf(stranger), home.requests)
    }

    @Test
    fun theOfficialChannelIsNeverTheFirstFriend() {
        val home = chatsHome(listOf(official, anya), archived = emptyList(), demo = false)

        assertEquals(anya, home.friends.first())
    }

    @Test
    fun anArchivedOfficialChannelLeavesThePersonAlone() {
        assertTrue(chatsHome(listOf(saved), archived = listOf(official.copy(archived = true)), demo = false).alone)
        assertFalse(chatsHome(listOf(saved), archived = listOf(anya.copy(archived = true)), demo = false).alone)
    }

    @Test
    fun notificationsAreNeededOnceSomeoneCanWrite() {
        val stranger = chat("vera", ConversationKind.Direct).copy(request = true)

        assertFalse(chatsHome(listOf(saved, official), archived = emptyList(), demo = false).someone)
        assertTrue(chatsHome(listOf(saved, official, anya), archived = emptyList(), demo = false).someone)
        assertTrue(chatsHome(listOf(saved, stranger), archived = emptyList(), demo = false).someone)
    }

    @Test
    fun theDemoHasNoNewcomerScreen() {
        assertFalse(chatsHome(listOf(saved), archived = emptyList(), demo = true).alone)
    }

    private fun chat(id: String, kind: ConversationKind) =
        Conversation(id = id, kind = kind, title = id, protection = Protection.EndToEnd, preview = null, updatedAt = Instant.EPOCH)
}
