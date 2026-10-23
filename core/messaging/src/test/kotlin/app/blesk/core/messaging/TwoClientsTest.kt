package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.calls.CallSignal
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.Audience
import app.blesk.core.data.settings.IncomingNote
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import app.blesk.shared.protocol.AttachmentPointer
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.InlineButton
import app.blesk.shared.protocol.LinkPreview
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.RoomRole
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceKind
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

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

    /**
     * [retryMillis] is how long its outbox waits after a failed pass when the server names no time. Closing the
     * repository cancels its [scope], so a client that is closed needs a scope of its own.
     */
    private inner class Client(
        val name: String,
        displayName: String,
        retryMillis: Long = NO_RETRY_IN_TIME,
        scope: CoroutineScope = this@TwoClientsTest.scope,
    ) {
        var privacy = Privacy()
        var photo: File? = null
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
            privacy = { privacy },
            profile = { OwnProfile(displayName, "", listOf("https://blesk.example/$name"), photo) },
            labels = { it?.javaClass?.simpleName.orEmpty() },
            log = EventLog(),
            linkPreview = { text ->
                LinkPreview(text.substringAfter("сюда "), "Страница", image = "A".repeat(LinkPreview.MAX_IMAGE_BYTES * 2))
            },
            retryMillis = retryMillis,
        )

        fun register() = runBlocking { assertEquals(RegisterResult.Ok, accounts.register(api, name)) }
    }

    @After
    fun tearDown() = scope.cancel()

    /** Waits for [block] to give a value; on timeout the failure points at the waiting line of the test. */
    private fun <T> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeoutOrNull(TIMEOUT) {
            var value: T? = null
            while (value == null) {
                value = block()
                if (value == null) delay(50)
            }
            value
        }
    } ?: throw AssertionError("nothing after ${TIMEOUT / 1000} s")

    private fun Client.messages(conversation: String): List<Message> = runBlocking { repo.messages(conversation).first() }

    /**
     * Waits until [to] has handled [text] from this client, shown or refused. A message is Sent once the server took
     * it, and this server hands it over before answering; whatever went ahead of it, like a profile, is there too.
     */
    private fun Client.handledBy(to: String, text: String) {
        eventually { messages(to).firstOrNull { it.text == text }?.takeIf { it.status >= SendStatus.Sent } }
        eventually { server.transport(to).takeIf { it.caughtUp } }
    }

    @Test
    fun strangersAndBlockedPeopleDoNotGetThrough() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.register()
        bob.register()
        alice.repo.start()
        bob.repo.start()
        bob.privacy = Privacy(write = Audience.Contacts)

        runBlocking { alice.repo.openDirect("bob") }
        alice.repo.send("bob", "первое")
        // Not just any ack: the profile goes ahead of a first message, and if Bob opened the chat while still
        // working on "первое", Alice would already be his contact and it would rightly get through.
        alice.handledBy("bob", "первое")
        assertTrue(bob.messages("alice").isEmpty())

        // Once Bob opens the chat himself, Alice is a contact and gets through.
        runBlocking { bob.repo.openDirect("alice") }
        alice.repo.send("bob", "второе")
        eventually { bob.messages("alice").firstOrNull()?.takeIf { it.text == "второе" } }

        bob.repo.setBlocked("alice", true)
        eventually { runBlocking { bob.repo.blocked.first() }.takeIf { list -> list.any { it.username == "alice" } } }
        alice.repo.send("bob", "третье")
        alice.handledBy("bob", "третье")
        assertTrue(bob.messages("alice").none { it.text == "третье" })
    }

    @Test
    fun profilePhotoGoesOnlyToThoseAllowed() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.photo = File(context.cacheDir, "alice.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        alice.register()
        bob.register()
        alice.repo.start()
        bob.repo.start()
        runBlocking { alice.repo.openDirect("bob") }
        alice.repo.send("bob", "привет")
        val shared = eventually { bob.repo.contact("alice")?.photo }
        assertTrue(File(shared).exists())
        assertEquals(listOf("https://blesk.example/alice"), bob.repo.contact("alice")?.links)

        alice.privacy = alice.privacy.copy(photo = Audience.Nobody)
        alice.repo.profileChanged()
        eventually { bob.repo.contact("alice")?.takeIf { it.photo == null } }
        assertTrue(!File(shared).exists())
    }

    @Test
    fun onlineStatusReachesContacts() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.register()
        bob.register()
        alice.repo.start()
        bob.repo.start()
        runBlocking { alice.repo.openDirect("bob") }
        alice.repo.send("bob", "привет")
        eventually { bob.messages("alice").firstOrNull() }

        alice.repo.setForeground(true)
        eventually { bob.repo.presence.value["alice"]?.takeIf { it.online } }
        alice.repo.setForeground(false)
        eventually { bob.repo.presence.value["alice"]?.takeIf { !it.online && it.lastSeen != null } }
    }

    @Test
    fun conversationEndToEnd() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.register()
        bob.register()
        alice.repo.start()
        bob.repo.start()

        assertEquals("bob", runBlocking { alice.repo.openDirect("bob") })
        // They know each other: a stranger's message would wait as a request, without receipts.
        runBlocking { bob.repo.openDirect("alice") }
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
        assertEquals(listOf("alice"), runBlocking { bob.repo.storedMedia() }.map { it.conversationId })
        runBlocking { bob.repo.clearMedia("alice", Long.MAX_VALUE) }
        assertTrue(!File(delivered.path).exists())
        assertEquals("", (bob.messages("alice").first().attachment as Attachment.File).path)
        assertTrue(server.transport("bob").acked.isNotEmpty())

        alice.repo.send("bob", "смотри сюда https://blesk.example/a")
        val card = eventually { bob.messages("alice").lastOrNull()?.link }
        // An oversized picture is dropped on arrival; the card itself stays.
        assertEquals(LinkPreview("https://blesk.example/a", "Страница"), card)
        alice.privacy = alice.privacy.copy(linkPreviews = false)
        alice.repo.send("bob", "и сюда https://blesk.example/b")
        val plain = eventually { bob.messages("alice").lastOrNull()?.takeIf { it.text.endsWith("/b") } }
        assertEquals(null, plain.link)
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

        // Voice channels open to all share one media key, replaced when someone is removed.
        val voice = alice.state(room).channels.first { it.kind == GroupChannelKind.Voice }.id
        val access = runBlocking { alice.repo.voiceAccess(room, voice) }
        assertNotNull(access)
        assertEquals(access, eventually { runBlocking { bob.repo.voiceAccess(room, voice) } })
        eventually { runBlocking { carol.repo.voiceAccess(room, voice) } }
        runBlocking { assertTrue(alice.repo.removeMember(room, "carol")) }
        val rotated = eventually { runBlocking { bob.repo.voiceAccess(room, voice) }?.takeIf { it != access } }
        assertEquals(runBlocking { alice.repo.voiceAccess(room, voice) }, rotated)
    }

    @Test
    fun aClosedVoiceChannelHasItsOwnKeyAndRoom() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val room = closedRoom(alice, listOf(bob, carol), moderators = listOf(bob))
        val onAir = runBlocking {
            assertTrue(alice.repo.addChannel(room.id, ON_AIR, GroupChannelKind.Voice, adminsOnly = false))
            val id = alice.state(room.id).channels.first { it.title == ON_AIR }.id
            assertTrue(alice.repo.change(room.id, GroupChange.HideChannel(id, setOf(GroupPolicy.EVERYONE))))
            id
        }
        val open = alice.state(room.id).channels.first { it.title == GroupEngine.DEFAULT_VOICE_CHANNEL }.id
        // Until Bob has the state that closes it, the channel is open to all on his phone and the room's key is its key.
        bob.caughtUpWith(alice, room.id)

        val access = eventually { runBlocking { bob.repo.voiceAccess(room.id, onAir) } }
        assertEquals(runBlocking { alice.repo.voiceAccess(room.id, onAir) }, access)
        val roomWide = runBlocking { alice.repo.voiceAccess(room.id, open) }!!
        assertNotEquals(roomWide.room, access.room)
        assertNotEquals(roomWide.key, access.key)
        // Whatever Alice sent Carol before it, Carol has handled once she has this.
        alice.repo.send(room.general, "всем привет")
        eventually { carol.messages(room.general).firstOrNull { it.text == "всем привет" } }
        assertNull(runBlocking { carol.repo.voiceAccess(room.id, onAir) })
        assertNull(runBlocking { carol.db.groups().voiceKey(room.id, onAir) })

        runBlocking { assertTrue(alice.repo.change(room.id, GroupChange.AssignRole("bob", MODS, on = false))) }
        bob.caughtUpWith(alice, room.id)
        val renewed = runBlocking { alice.repo.voiceAccess(room.id, onAir) }!!
        assertNotEquals(access.room, renewed.room)
        assertNotEquals(access.key, renewed.key)
        assertNull(runBlocking { bob.repo.voiceAccess(room.id, onAir) })
    }

    @Test
    fun aClosedChannelIsReadOnlyByThoseWhoSeeIt() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val room = closedRoom(alice, listOf(bob, carol), moderators = listOf(bob))
        val notes = carol.notes()
        val sealed = sealedTo("carol")

        alice.repo.send(room.closed, "тайный план")
        eventually { bob.messages(room.closed).firstOrNull { it.text == "тайный план" } }
        // Whatever Alice sent Carol before it, Carol has handled once she has this.
        alice.repo.send(room.general, "всем привет")
        eventually { carol.messages(room.general).firstOrNull { it.text == "всем привет" } }
        assertEquals(sealed + 1, sealedTo("carol"))

        bob.repo.send(room.closed, "тайный ответ")
        eventually { alice.messages(room.closed).firstOrNull { it.text == "тайный ответ" } }
        bob.repo.send(room.general, "и всем")
        eventually { carol.messages(room.general).firstOrNull { it.text == "и всем" } }
        assertEquals(sealed + 2, sealedTo("carol"))

        assertTrue(carol.messages(room.closed).isEmpty())
        assertTrue(runBlocking { carol.repo.search("тайный") }.isEmpty())
        assertEquals(emptyList<IncomingNote>(), notes.toList())
    }

    @Test
    fun someoneWhoLostAChannelCannotOpenItsNextMessage() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val room = closedRoom(alice, listOf(bob, carol), moderators = listOf(bob, carol))
        alice.repo.send(room.closed, "тайный план")
        eventually { bob.messages(room.closed).firstOrNull { it.text == "тайный план" } }
        eventually { carol.messages(room.closed).firstOrNull { it.text == "тайный план" } }

        runBlocking { assertTrue(alice.repo.change(room.id, GroupChange.AssignRole("bob", MODS, on = false))) }
        bob.caughtUpWith(alice, room.id)
        eventually { bob.messages(room.closed).takeIf { it.isEmpty() } }
        assertNull(runBlocking { bob.db.conversations().get(room.closed) })
        assertTrue(runBlocking { bob.repo.search("тайный") }.isEmpty())

        val toCarol = sealedTo("carol")
        val toBob = sealedTo("bob")
        alice.repo.send(room.closed, "тайный план Б")
        eventually { carol.messages(room.closed).firstOrNull { it.text == "тайный план Б" } }
        val body = sealedFrames("carol").drop(toCarol).single().envelope.body
        // Not even with the ciphertext in hand, from a server that kept a copy for him.
        assertThrows(Exception::class.java) { GroupCrypto(bob.store) { "bob" }.decrypt("alice", body) }

        alice.repo.send(room.general, "всем привет")
        eventually { bob.messages(room.general).firstOrNull { it.text == "всем привет" } }
        assertEquals(toBob + 1, sealedTo("bob"))
    }

    @Test
    fun aChannelGivenBeforeItsUpdateArrivesIsHeldNotLost() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val room = closedRoom(alice, listOf(bob, carol), moderators = listOf(carol))
        val update = server.holdBetween("alice", "bob")
        runBlocking { assertTrue(alice.repo.change(room.id, GroupChange.AssignRole("bob", MODS, on = true))) }
        carol.caughtUpWith(alice, room.id)

        // Carol has heard Bob may read the channel and writes to him too, before he hears it from Alice.
        val fromCarol = sealedFrom("carol", to = "bob")
        carol.repo.send(room.closed, "добро пожаловать")
        eventually { sealedFrom("carol", to = "bob").takeIf { it > fromCarol } }
        eventually { server.transport("bob").takeIf { it.caughtUp } }
        assertTrue(bob.messages(room.closed).isEmpty())
        assertNull(runBlocking { bob.db.conversations().get(room.closed) })

        update.complete(Unit)
        eventually { bob.messages(room.closed).firstOrNull { it.text == "добро пожаловать" } }
        eventually { server.transport("bob").takeIf { it.caughtUp } }
        assertEquals(1, bob.messages(room.closed).count { it.text == "добро пожаловать" })
    }

    @Test
    fun aLateSenderDoesNotShowAClosedChannelToSomeoneWhoLostIt() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val room = closedRoom(alice, listOf(bob, carol), moderators = listOf(bob, carol))
        val update = server.holdBetween("alice", "carol")
        runBlocking { assertTrue(alice.repo.change(room.id, GroupChange.AssignRole("bob", MODS, on = false))) }
        bob.caughtUpWith(alice, room.id)
        val notes = bob.notes()

        // Carol has not heard yet: by her state Bob still reads the channel, and she writes to him.
        val fromCarol = sealedFrom("carol", to = "bob")
        carol.repo.send(room.closed, "тайный план")
        eventually { sealedFrom("carol", to = "bob").takeIf { it > fromCarol } }
        eventually { server.transport("bob").takeIf { it.caughtUp } }
        assertTrue(bob.messages(room.closed).isEmpty())
        assertTrue(runBlocking { bob.repo.search("тайный") }.isEmpty())
        assertEquals(emptyList<IncomingNote>(), notes.toList())
        eventually { alice.messages(room.closed).firstOrNull { it.text == "тайный план" } }
        update.complete(Unit)
    }

    @Test
    fun aRoomKeyMessageForAHiddenChannelLeavesNoTrace() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        val carol = Client("carol", "Кэрол")
        val room = closedRoom(alice, listOf(bob, carol), moderators = listOf(bob))
        alice.repo.send(room.general, "всем привет")
        eventually { carol.messages(room.general).firstOrNull { it.text == "всем привет" } }
        val notes = carol.notes()
        server.heldDownloads = CompletableDeferred()

        // A build from before closed channels had keys of their own: the room's one key, which Carol holds.
        val roomKey = UUID.fromString(runBlocking { alice.db.groups().senderKey(room.id, "") }!!.distributionId)
        val file = AttachmentPointer("blob-plan", "a2V5", "file", "plan.pdf", size = 10)
        val text = Body.Text("тайный план", attachment = file)
        val leaked = Content(UUID.randomUUID().toString(), System.currentTimeMillis(), text, group = room.closed)
        val body = GroupCrypto(alice.store) { "alice" }.encrypt(roomKey, leaked)
        runBlocking { alice.api.send("carol", OutgoingMessage(leaked.id, EnvelopeType.SENDERKEY, body)) }
        alice.repo.send(room.general, "и снова всем")
        eventually { carol.messages(room.general).firstOrNull { it.text == "и снова всем" } }

        assertNull(runBlocking { carol.db.messages().get(leaked.id) })
        assertTrue(carol.messages(room.closed).isEmpty())
        assertTrue(runBlocking { carol.repo.search("тайный") }.isEmpty())
        assertEquals(emptyList<IncomingNote>(), notes.toList())
        assertEquals(0, server.downloadsHeld.get())
    }

    /** The general and the closed channel of a room, as conversations. */
    private class ClosedRoom(val id: String, val general: String, val closed: String)

    /**
     * Alice's room with [others] in it, and «модерация», a channel only the moderators' role sees, which [moderators]
     * hold. Returns once everyone has the room as Alice left it.
     */
    private fun closedRoom(alice: Client, others: List<Client>, moderators: List<Client>): ClosedRoom {
        (listOf(alice) + others).forEach {
            it.register()
            it.repo.start()
        }
        val room = runBlocking {
            others.forEach { alice.repo.openDirect(it.name) }
            alice.repo.createGroup(GroupKind.Room, "Клуб", others.map { it.name })
        }
        val closed = runBlocking {
            assertTrue(alice.repo.change(room, GroupChange.SaveRole(RoomRole(MODS, "Модераторы"))))
            assertTrue(alice.repo.addChannel(room, CLOSED, GroupChannelKind.Text, adminsOnly = false))
            val closed = alice.state(room).channels.first { it.title == CLOSED }.id
            assertTrue(alice.repo.change(room, GroupChange.HideChannel(closed, setOf(GroupPolicy.EVERYONE))))
            moderators.forEach { assertTrue(alice.repo.change(room, GroupChange.AssignRole(it.name, MODS, on = true))) }
            closed
        }
        others.forEach { it.caughtUpWith(alice, room) }
        val general = alice.state(room).channels.first { it.title == GroupEngine.DEFAULT_TEXT_CHANNEL }.id
        return ClosedRoom(room, GroupEngine.channelConversation(room, general), GroupEngine.channelConversation(room, closed))
    }

    private fun Client.state(group: String): GroupState = runBlocking { repo.group(group).first() }!!

    private fun Client.caughtUpWith(other: Client, group: String) {
        val version = other.state(group).version
        eventually { runBlocking { repo.group(group).first() }?.takeIf { it.version >= version } }
    }

    /** What this client announces from now on, gathered as it comes. */
    private fun Client.notes(): List<IncomingNote> {
        val notes = CopyOnWriteArrayList<IncomingNote>()
        val subscribed = CompletableDeferred<Unit>()
        scope.launch { repo.incoming.onSubscription { subscribed.complete(Unit) }.collect { notes += it } }
        runBlocking { subscribed.await() }
        return notes
    }

    /** Group messages, sealed with someone's sender key, the server has handed [name] so far. */
    private fun sealedFrames(name: String) = server.transport(name).frames.replayCache
        .filterIsInstance<ServerFrame.Deliver>()
        .filter { it.envelope.type == EnvelopeType.SENDERKEY }

    private fun sealedTo(name: String) = sealedFrames(name).size

    private fun sealedFrom(sender: String, to: String) = sealedFrames(to).count { it.envelope.sender == sender }

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

        // Full-text search folds case for Cyrillic and matches word beginnings.
        assertEquals("исчезнет", runBlocking { bob.repo.search("ИСЧЕЗ") }.firstOrNull()?.text)

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

    /** Alice and Bob, each with the other as a contact and a session between them already. */
    private fun acquainted(alice: Client, bob: Client) {
        listOf(alice, bob).forEach {
            it.register()
            it.repo.start()
        }
        runBlocking {
            alice.repo.openDirect("bob")
            bob.repo.openDirect("alice")
        }
        alice.repo.send("bob", "первое")
        alice.handledBy("bob", "первое")
    }

    @Test
    fun aServerErrorIsRetriedWithoutAnotherSend() {
        val alice = Client("alice", "Алиса", retryMillis = 200)
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)

        server.refuseSends("alice", FakeServer.Refusal(HttpStatusCode.InternalServerError))
        alice.repo.send("bob", "второе")
        eventually { bob.messages("alice").firstOrNull { it.text == "второе" } }
        eventually { alice.messages("bob").firstOrNull { it.text == "второе" }?.takeIf { it.status >= SendStatus.Sent } }
    }

    @Test
    fun aBusyServerIsAskedAgainWhenItSaid() {
        // Alice's own pause is far beyond the test's patience: only the server's Retry-After can bring the message.
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)

        server.refuseSends(
            "alice",
            FakeServer.Refusal(HttpStatusCode.TooManyRequests, retryAfterSeconds = 1),
            FakeServer.Refusal(HttpStatusCode.ServiceUnavailable, retryAfterSeconds = 1),
        )
        alice.repo.send("bob", "второе")
        eventually { bob.messages("alice").firstOrNull { it.text == "второе" } }
    }

    @Test
    fun anExpiredTokenIsRenewedAndTheMessageGoesAtOnce() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)
        val tokens = server.tokensIssued.get()

        server.refuseSends("alice", FakeServer.Refusal(HttpStatusCode.Unauthorized))
        alice.repo.send("bob", "второе")
        eventually { bob.messages("alice").firstOrNull { it.text == "второе" } }
        assertEquals(tokens + 1, server.tokensIssued.get())
    }

    @Test
    fun aClosedRepositoryAsksTheServerNothingMore() {
        val own = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val alice = Client("alice", "Алиса", retryMillis = 200, scope = own)
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)

        // The server is slow to refuse and names when to ask again; the repository closes while the answer is on its way.
        val answer = CompletableDeferred<Unit>()
        server.refuseSends("alice", FakeServer.Refusal(HttpStatusCode.TooManyRequests, retryAfterSeconds = 1, answerAfter = answer))
        alice.repo.send("bob", "второе")
        eventually { server.refusalsHeld.get().takeIf { it == 1 } }
        val tokens = server.tokensIssued.get()

        alice.repo.close()
        answer.complete(Unit)
        // Twice the pause the server named: an outbox still running would have sent by now.
        runBlocking { delay(2_000) }
        assertTrue(bob.messages("alice").none { it.text == "второе" })
        assertEquals(tokens, server.tokensIssued.get())
        assertTrue(server.transport("alice").stopped)
        assertFalse(own.isActive)

        // Only Alice's repository closed: Bob's, on the shared scope, still sends.
        assertTrue(scope.isActive)
        bob.repo.send("alice", "ответ")
        eventually { bob.messages("alice").firstOrNull { it.text == "ответ" }?.takeIf { it.status >= SendStatus.Sent } }
    }

    @Test
    fun aTokenRefusedRightAfterSigningInWaitsForTheNextSend() {
        val alice = Client("alice", "Алиса", retryMillis = 200)
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)
        val tokens = server.tokensIssued.get()

        server.refuseSends("alice", FakeServer.Refusal(HttpStatusCode.Unauthorized), FakeServer.Refusal(HttpStatusCode.Unauthorized))
        alice.repo.send("bob", "второе")
        eventually { server.tokensIssued.get().takeIf { it > tokens } }
        // Several of Alice's pauses: a timer would have signed in again and sent by now.
        runBlocking { delay(1_000) }
        assertEquals(tokens + 1, server.tokensIssued.get())
        assertTrue(bob.messages("alice").none { it.text == "второе" })

        alice.repo.send("bob", "третье")
        val got = eventually { bob.messages("alice").map { it.text }.takeIf { "третье" in it } }
        assertEquals(listOf("второе", "третье"), got.filter { it in setOf("второе", "третье") })
    }

    @Test
    fun theLatestFailureSetsWhenToAskAgain() {
        val alice = Client("alice", "Алиса", retryMillis = 300)
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)

        // A busy server asks for ten minutes; the next try, made by a new send, only loses the connection.
        server.refuseSends(
            "alice",
            FakeServer.Refusal(HttpStatusCode.ServiceUnavailable, retryAfterSeconds = 600),
            FakeServer.Refusal(status = null),
        )
        alice.repo.send("bob", "второе")
        eventually { runBlocking { alice.db.outbox().all() }.firstOrNull()?.takeIf { it.attempts > 0 } }
        // No need to wait for the refused pass to end: a send while it finishes gets its own pass right after it.
        alice.repo.send("bob", "третье")
        eventually { bob.messages("alice").firstOrNull { it.text == "третье" } }
    }

    @Test
    fun aSignInThatGoesWrongStopsNothing() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)

        // Alice's receipt for "второе" finds her token refused, and the sign-in after it gets no challenge back.
        server.refuseSends("alice", FakeServer.Refusal(HttpStatusCode.Unauthorized))
        server.garbledChallenges = 1
        bob.repo.send("alice", "второе")
        eventually { server.garbledChallenges.takeIf { it == 0 } }

        bob.repo.send("alice", "третье")
        eventually { alice.messages("bob").firstOrNull { it.text == "третье" } }
        eventually { bob.messages("alice").firstOrNull { it.text == "второе" }?.takeIf { it.status >= SendStatus.Delivered } }
    }

    @Test
    fun aMessageTheServerRefusesForGoodFailsOnceAndLeavesTheQueue() {
        val alice = Client("alice", "Алиса", retryMillis = 200)
        val bob = Client("bob", "Боб")
        acquainted(alice, bob)

        server.refuseSends("alice", FakeServer.Refusal(HttpStatusCode.Forbidden))
        alice.repo.send("bob", "второе")
        eventually { alice.messages("bob").firstOrNull { it.text == "второе" }?.takeIf { it.status == SendStatus.Failed } }
        assertTrue(runBlocking { alice.db.outbox().all() }.isEmpty())
        assertTrue(bob.messages("alice").none { it.text == "второе" })
    }

    @Test
    fun aBlockMadeWhileTheirProfileArrivesStays() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.photo = File(context.cacheDir, "alice.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        listOf(alice, bob).forEach {
            it.register()
            it.repo.start()
        }
        runBlocking { bob.repo.openDirect("alice") }
        val photoOnItsWay = CompletableDeferred<Unit>().also { server.heldDownloads = it }

        runBlocking { alice.repo.openDirect("bob") }
        alice.repo.send("bob", "привет")
        // Bob is in the middle of taking Alice's profile: the photo is coming, the name is not stored yet.
        eventually { server.downloadsHeld.get().takeIf { it > 0 } }
        bob.repo.setBlocked("alice", true)
        eventually { bob.contactRow("alice")?.takeIf { it.blocked } }
        photoOnItsWay.complete(Unit)

        val alicesRow = eventually { bob.contactRow("alice")?.takeIf { it.photo != null } }
        assertEquals("Алиса", alicesRow.name)
        assertTrue(alicesRow.blocked)
    }

    @Test
    fun aChatOpenedWhileTheirProfileArrivesKeepsThemAContact() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.photo = File(context.cacheDir, "alice.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        listOf(alice, bob).forEach {
            it.register()
            it.repo.start()
        }
        val photoOnItsWay = CompletableDeferred<Unit>().also { server.heldDownloads = it }

        runBlocking { alice.repo.openDirect("bob") }
        alice.repo.send("bob", "привет")
        eventually { server.downloadsHeld.get().takeIf { it > 0 } }
        runBlocking { bob.repo.openDirect("alice") }
        photoOnItsWay.complete(Unit)

        val alicesRow = eventually { bob.contactRow("alice")?.takeIf { it.photo != null } }
        assertEquals("Алиса", alicesRow.name)
        assertTrue(alicesRow.added)
        eventually { runBlocking { bob.repo.conversation("alice").first() }?.takeIf { it.title == "Алиса" } }
    }

    @Test
    fun aServiceHandleIsRefusedOnThePhone() {
        val alice = Client("alice", "Алиса")
        alice.register()
        // This server has no spaces route: a request would come back as a failure, not as an invalid handle.
        for (handle in listOf("blesk", "support24", "mine")) {
            val result = runBlocking { alice.repo.createSpace(SpaceKind.Channel, handle, "x", "") }
            assertEquals(handle, SpaceResult.HandleInvalid to null, result)
        }
    }

    @Test
    fun aLiveEditKeepsTheSpacePostAndARemovalTakesItAway() {
        val alice = Client("alice", "Алиса")
        alice.register()
        alice.repo.start()
        val socket = server.transport("alice")
        val post = Post(1, "club", NewPost.MAIN_CHANNEL, "bob", "анонс", createdAtMillis = 1)
        fun row(id: Long) = runBlocking { alice.db.messages().get(SpaceEngine.messageId("club", id)) }

        socket.push(ServerFrame.SpacePost(post))
        socket.push(ServerFrame.SpacePost(post.copy(id = 2, text = "второй")))
        socket.push(ServerFrame.SpacePostChanged("club", 1, post.copy(text = "анонс!", editedAtMillis = 2)))
        socket.push(ServerFrame.SpacePostChanged("club", 2))
        // Frames are handled in order, so once this post is here the changes before it have been handled.
        socket.push(ServerFrame.SpacePost(post.copy(id = 3, text = "третий")))
        eventually { row(3) }

        assertEquals("анонс!", row(1)?.text)
        assertNull(row(2))
    }

    private fun Client.contactRow(username: String) = runBlocking { db.contacts().get(username) }

    private companion object {
        // Only a ceiling: a passing check returns at once. The full build runs the server tests and the
        // Kyber key generation of several clients side by side, where 20 seconds was sometimes not enough.
        const val TIMEOUT = 60_000L

        /** An outbox pause no test waits out, so whatever gets through came by another way. */
        const val NO_RETRY_IN_TIME = 10 * TIMEOUT

        const val MODS = "mods"
        const val CLOSED = "модерация"
        const val ON_AIR = "эфир"
    }
}
