package app.blesk.core.data.demo

import app.blesk.core.data.SpaceResult
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.SpaceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCommunitiesTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val seed = DemoSeed(
        contacts = listOf(Contact("anya", "Аня", "anya"), Contact("misha", "Миша", "misha.k")),
        conversations = emptyList(),
        messages = emptyList(),
        echoBotConversationId = "bot",
        echoBotAuthorId = "bot",
        directContactIds = setOf("anya", "misha"),
        community = DemoCommunityTexts(textChannel = "общее", voiceChannel = "созвон", greeting = "привет"),
    )
    private val demo = DemoRepository(seed, { "" }, scope, EventLog())

    @After
    fun stop() = scope.cancel()

    @Test
    fun groupWithDemoPeopleAppearsInTheList() = runBlocking {
        val id = demo.createGroup(GroupKind.Group, " Поход ", listOf("anya", "misha.k"))
        val row = demo.conversations.first().single { it.id == id }
        assertEquals(ConversationKind.Group, row.kind)
        assertEquals("Поход", row.title)
        assertEquals(3, row.memberCount)
        assertEquals(Protection.EndToEnd, row.protection)
        assertEquals("Миша", demo.contact("misha.k")?.name)
        assertTrue(demo.rename(id, "Сплав"))
        assertEquals("Сплав", demo.group(id).first()?.title)
    }

    @Test
    fun roomGetsItsChannelsAndLeavingClearsThem() = runBlocking {
        val id = demo.createGroup(GroupKind.Room, "Клуб", listOf("anya"))
        val channel = demo.conversation(id).first()?.channels?.first()
        assertNotNull(channel)
        assertEquals(id, demo.conversation(channel!!.id).first()?.parentId)
        demo.leave(id)
        assertNull(demo.conversation(id).first())
        assertNull(demo.conversation(channel.id).first())
    }

    @Test
    fun channelIsOpenAndHandlesAreChecked() = runBlocking {
        val (result, id) = demo.createSpace(SpaceKind.Channel, "hike", "Походы", "")
        assertEquals(SpaceResult.Ok, result)
        assertTrue(demo.isSpace(id!!))
        assertEquals(Protection.Open, demo.conversation(id).first()?.protection)
        assertEquals(SpaceResult.HandleTaken, demo.createSpace(SpaceKind.Channel, "hike", "Ещё", "").first)
        assertEquals(SpaceResult.HandleTaken, demo.createSpace(SpaceKind.Room, "anya", "Аня", "").first)
        assertEquals(SpaceResult.HandleInvalid, demo.createSpace(SpaceKind.Room, "a!", "x", "").first)
        assertEquals(SpaceResult.HandleInvalid, demo.createSpace(SpaceKind.Channel, "b1esk", "x", "").first)
        assertTrue(demo.deleteSpace("hike"))
        assertFalse(demo.isSpace(id))
    }
}
