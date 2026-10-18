package app.blesk.navigation

import app.blesk.R
import app.blesk.core.data.demo.DemoCommunityTexts
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Contact
import app.blesk.core.network.ServerAddress
import app.blesk.shared.protocol.ChannelLink
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.Invites
import app.blesk.shared.protocol.ProfileLink
import app.blesk.shared.protocol.SpaceKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val world = DemoRepository(
        DemoSeed(
            contacts = listOf(Contact("anya", "Аня", "anya")),
            conversations = emptyList(),
            messages = emptyList(),
            echoBotConversationId = "bot",
            echoBotAuthorId = "bot",
            directContactIds = setOf("anya"),
            community = DemoCommunityTexts(textChannel = "общее", voiceChannel = "созвон", greeting = "привет"),
        ),
        { "" },
        scope,
        EventLog(),
    )
    private val code = "abcdefghjk23"
    private val groupInvite = InviteLink.format("6f1c2a9e-7b40-4d1e", "vera.s", "Hk3_9xQe-Lm2")
    private val pendingInvite = MutableStateFlow<String?>(null)
    private val opened = mutableListOf<String>()
    private val screens = LinkScreens(
        conversation = { opened += "chat:$it" },
        profile = { opened += "profile:$it" },
        registration = { opened += "registration" },
    )

    @After
    fun stop() = scope.cancel()

    @Test
    fun `every kind of link is told apart`() {
        assertEquals(LinkTarget.Registration(code), LinkTarget.of(Invites.link(code)))
        assertEquals(LinkTarget.Registration(code), LinkTarget.of("www.blesk.su/i/${code.uppercase()}"))
        assertEquals(LinkTarget.Profile("vera.s", null), LinkTarget.of("https://blesk.su/@vera.s"))
        assertEquals(LinkTarget.Profile("vera.s", null), LinkTarget.of("blesk://u/vera.s"))
        assertEquals(LinkTarget.Space("news"), LinkTarget.of(ChannelLink.format("news")))
        assertEquals(LinkTarget.Group(groupInvite, "vera.s"), LinkTarget.of(" $groupInvite "))
        assertEquals(
            LinkTarget.Server(ServerAddress("https://chat.example.ru")),
            LinkTarget.of("blesk://server?url=https://chat.example.ru"),
        )
    }

    @Test
    fun `a username, a bare code or another site is no link`() {
        assertNull(LinkTarget.of("anya"))
        assertNull(LinkTarget.of("@anya"))
        assertNull(LinkTarget.of(code))
        assertNull(LinkTarget.of("chat.example.ru"))
        assertNull(LinkTarget.of("https://blesk.su.evil.com/i/$code"))
        assertNull(LinkTarget.of("https://evil.com/@vera.s"))
    }

    @Test
    fun `in the demo an invite waits for the registration form`() = runBlocking {
        val outcome = follow(LinkTarget.Registration(code), world, demo = true, pendingInvite, screens)
        assertEquals(LinkOutcome.Opened, outcome)
        assertEquals(code, pendingInvite.value)
        assertEquals(listOf("registration"), opened)
    }

    @Test
    fun `in the demo nobody is looked up and the registration form opens`() = runBlocking {
        val targets = listOf(
            LinkTarget.Profile("anya", null),
            LinkTarget.Space("news"),
            LinkTarget.Group(groupInvite, "vera.s"),
        )
        targets.forEach { target ->
            assertEquals(target.toString(), LinkOutcome.NeedsAccount, follow(target, world, demo = true, pendingInvite, screens))
        }
        assertEquals(List(targets.size) { "registration" }, opened)
        assertNull(pendingInvite.value)
        assertEquals(R.string.link_needs_account, LinkOutcome.NeedsAccount.notice)
    }

    @Test
    fun `a server code opens the registration form only in the demo`() = runBlocking {
        val own = LinkTarget.Server(ServerAddress.DEFAULT)
        val other = LinkTarget.Server(ServerAddress("https://chat.example.ru"))
        assertEquals(LinkOutcome.Opened, follow(own, world, demo = true, pendingInvite, screens))
        assertEquals(LinkOutcome.OtherServer, follow(other, world, demo = true, pendingInvite, screens))
        assertEquals(listOf("registration", "registration"), opened)
        assertEquals(LinkOutcome.AlreadyConnected, follow(own, world, demo = false, pendingInvite, screens))
    }

    @Test
    fun `with an account an invite is kept for a friend`() = runBlocking {
        val outcome = follow(LinkTarget.Registration(code), world, demo = false, pendingInvite, screens)
        assertEquals(LinkOutcome.AlreadyRegistered, outcome)
        assertNull(pendingInvite.value)
        assertTrue(opened.isEmpty())
        assertEquals(R.string.link_invite_registered, outcome.notice)
    }

    @Test
    fun `with an account a profile link opens the profile`() = runBlocking {
        assertEquals(LinkOutcome.Opened, follow(LinkTarget.Profile("anya", null), world, demo = false, pendingInvite, screens))
        assertEquals(listOf("profile:anya"), opened)
        assertEquals(LinkOutcome.NotFound, follow(LinkTarget.Profile("nobody", null), world, demo = false, pendingInvite, screens))
        assertNull(LinkOutcome.Opened.notice)
    }

    @Test
    fun `with an account a channel link joins it and opens it`() = runBlocking {
        val (_, id) = world.createSpace(SpaceKind.Room, "club", "Клуб", "")
        assertEquals(LinkOutcome.Opened, follow(LinkTarget.Space("club"), world, demo = false, pendingInvite, screens))
        assertEquals(listOf("chat:$id"), opened)
        assertEquals(LinkOutcome.NotFound, follow(LinkTarget.Space("nothing"), world, demo = false, pendingInvite, screens))
    }

    @Test
    fun `find opens a username or a link and nothing else`() = runBlocking {
        var joinSent = false
        assertTrue(find(world, "@anya", { opened += it }) { joinSent = true })
        assertTrue(find(world, ProfileLink.format("anya", "Qx7_kP2-mN4wZ9aB"), { opened += it }) { joinSent = true })
        assertFalse(find(world, Invites.link(code), { opened += it }) { joinSent = true })
        assertFalse(find(world, groupInvite, { opened += it }) { joinSent = true })
        assertEquals(listOf("anya", "anya"), opened)
        assertFalse(joinSent)
    }

    @Test
    fun `only a link that shows the person to others is asked about`() {
        assertTrue(LinkTarget.Group(groupInvite, "vera.s").revealsPerson)
        assertTrue(LinkTarget.Space("news").revealsPerson)
        assertFalse(LinkTarget.Profile("anya", null).revealsPerson)
        assertFalse(LinkTarget.Registration(code).revealsPerson)
        assertFalse(LinkTarget.Server(ServerAddress.DEFAULT).revealsPerson)
    }

    @Test
    fun `a sent link waits behind the lock`() = runBlocking {
        val link = ProfileLink.format("anya", null)
        val requests = MutableStateFlow<String?>(link)
        var locked = true
        val followed = mutableListOf<String>()
        val waiting = launch(Dispatchers.Unconfined) { deliverLinks(requests, { locked }, scope) { followed += it } }
        assertEquals(link, requests.value)
        waiting.cancel()
        locked = false
        val open = launch(Dispatchers.Unconfined) { deliverLinks(requests, { locked }, scope) { followed += it } }
        assertNull(requests.value)
        assertEquals(listOf(link), followed)
        open.cancel()
    }

    @Test
    fun `a followed link is not cut off when its collection ends`() = runBlocking {
        val link = ChannelLink.format("news")
        val requests = MutableStateFlow<String?>(link)
        val answer = CompletableDeferred<Unit>()
        val followed = mutableListOf<String>()
        val collection = launch(Dispatchers.Unconfined) {
            deliverLinks(requests, { false }, scope) {
                answer.await()
                followed += it
            }
        }
        assertNull(requests.value)
        collection.cancel()
        answer.complete(Unit)
        assertEquals(listOf(link), followed)
    }

    @Test
    fun `a space search never sends a link to the server`() {
        assertEquals("news", spaceQuery("news"))
        assertEquals("news", spaceQuery("https://blesk.su/c/news"))
        assertNull(spaceQuery(ProfileLink.format("anya", "Qx7_kP2-mN4wZ9aB")))
        assertNull(spaceQuery(groupInvite))
        assertNull(spaceQuery(Invites.link(code)))
    }
}
