package app.blesk.server

import app.blesk.server.auth.Tokens
import app.blesk.shared.protocol.AnswerCallback
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentPointer
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallToken
import app.blesk.shared.protocol.CallTokenRequest
import app.blesk.shared.protocol.CallbackPress
import app.blesk.shared.protocol.Calls
import app.blesk.shared.protocol.ClientFrame
import app.blesk.shared.protocol.CreateSpaceChannel
import app.blesk.shared.protocol.Discovery
import app.blesk.shared.protocol.EditMessage
import app.blesk.shared.protocol.EditPost
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.InlineButton
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.RecoveryHash
import app.blesk.shared.protocol.RoleChange
import app.blesk.shared.protocol.SendMessage
import app.blesk.shared.protocol.SentMessage
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import app.blesk.shared.protocol.Update
import app.blesk.shared.protocol.UpdateSpace
import app.blesk.shared.protocol.UploadStart
import app.blesk.shared.protocol.UploadStarted
import com.auth0.jwt.JWT
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

/**
 * The server trusts no client: a rebuilt app can do exactly what the official one does and no more.
 * Every route is called here without credentials, with someone else's credentials and on someone
 * else's resources, and must refuse.
 */
class AuthorizationTest {
    @Test
    fun `every account route refuses a missing, forged, expired or misplaced token`() = serverTest { harness ->
        val alice = harness.register()
        val expired = Tokens.newToken()
        harness.services.auth.saveToken(Tokens.hash(expired), harness.accountId(alice), harness.services.now().minusSeconds(1))
        val bot = harness.createBot(alice)

        AccountRoutes.forEach { (method, path) ->
            val attempts = mapOf<String, suspend () -> HttpResponse>(
                "none" to { harness.call(method, path) },
                "forged" to { harness.call(method, path) { bearerAuth(Tokens.newToken()) } },
                "expired" to { harness.call(method, path) { bearerAuth(expired) } },
                "bot token" to { harness.call(method, path) { header(HttpHeaders.Authorization, "Bearer ${bot.token}") } },
                // Only the socket takes its token from the URL.
                "query token" to { harness.call(method, "$path?token=${alice.token}") },
            )
            attempts.forEach { (kind, attempt) ->
                val response = attempt()
                assertEquals("$method $path with $kind", HttpStatusCode.Unauthorized, response.status)
                assertEquals("$method $path with $kind", "unauthorized", response.body<ErrorBody>().code)
            }
        }
    }

    @Test
    fun `the socket refuses a missing, forged, expired or bot token`() = serverTest { harness ->
        val alice = harness.register()
        val expired = Tokens.newToken()
        harness.services.auth.saveToken(Tokens.hash(expired), harness.accountId(alice), harness.services.now().minusSeconds(1))
        val bot = harness.createBot(alice)
        listOf(Api.SOCKET, "${Api.SOCKET}?token=forged", "${Api.SOCKET}?token=$expired").forEach { url ->
            assertTrue(url, runCatching { harness.client.webSocket(url) { nextFrame() } }.isFailure)
        }
        val asBot = runCatching {
            harness.client.webSocket(Api.SOCKET, request = { header(HttpHeaders.Authorization, "Bearer ${bot.token}") }) { nextFrame() }
        }
        assertTrue(asBot.isFailure)
    }

    @Test
    fun `every bot API method refuses a missing, forged, person's, replaced or deleted bot token`() = serverTest { harness ->
        val owner = harness.register()
        val replaced = harness.createBot(owner)
        val renewal = harness.client.post("${Bots.userPath(replaced.bot.username)}/token") { bearerAuth(owner.token) }
        assertEquals(HttpStatusCode.OK, renewal.status)
        val deleted = harness.createBot(owner)
        harness.call(HttpMethod.Delete, Bots.userPath(deleted.bot.username)) { bearerAuth(owner.token) }

        val tokens = listOf(null, "${replaced.bot.username}:forged", owner.token, replaced.token, deleted.token)
        BotApiRoutes.forEach { (method, name) ->
            tokens.forEach { token ->
                val response = harness.call(method, "${Bots.API_PATH}/$name") {
                    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
                assertEquals("$method $name with $token", HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `a token is only issued to the key that owns the account`() = serverTest { harness ->
        val alice = harness.register()
        val mallory = harness.register()
        val nonce = harness.challenge(alice.username).nonce
        assertEquals(HttpStatusCode.Unauthorized, harness.tokenRaw(alice.username, nonce, mallory.identity).status)
        // A nonce issued for one name does not open another.
        val ownNonce = harness.challenge(mallory.username).nonce
        assertEquals(HttpStatusCode.Unauthorized, harness.tokenRaw(alice.username, ownNonce, alice.identity).status)
    }

    @Test
    fun `account settings act on the caller only`() = serverTest { harness ->
        val alice = harness.register()
        val mallory = harness.register()
        val hash = Random.nextBytes(32).b64()
        assertEquals(HttpStatusCode.NoContent, harness.client.put(Api.RECOVERY) { json(alice, RecoveryHash(hash)) }.status)
        // Claiming someone else's recovery hash would let the phrase open the wrong account.
        assertEquals(HttpStatusCode.Conflict, harness.client.put(Api.RECOVERY) { json(mallory, RecoveryHash(hash)) }.status)

        harness.client.put(Api.DISCOVERY) { json(alice, Discovery(false)) }
        harness.client.put(Api.DISCOVERY) { json(mallory, Discovery(true)) }
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.ACCOUNTS}/${alice.username}") { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.KEYS}/${alice.username}") { bearerAuth(mallory.token) }.status)

        assertEquals(HttpStatusCode.NoContent, harness.call(HttpMethod.Delete, Api.ACCOUNT) { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.OK, harness.client.get(Api.KEY_STATUS) { bearerAuth(alice.token) }.status)
    }

    @Test
    fun `keys are accepted only when signed by the account's own identity`() = serverTest { harness ->
        val alice = harness.register()
        val mallory = harness.register()
        // Keys signed by Alice, uploaded under Mallory's account.
        val response = harness.uploadKeys(mallory, harness.keys(alice, preKeys = 1, kyberPreKeys = 1))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_signature", response.body<ErrorBody>().code)
    }

    @Test
    fun `messages go to people only and an ack removes only the caller's own`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val mallory = harness.register()
        val bot = harness.createBot(alice)
        val message = OutgoingMessage(UUID.randomUUID().toString(), EnvelopeType.WHISPER, Random.nextBytes(32).b64())
        assertEquals(HttpStatusCode.NotFound, harness.client.post("${Api.MESSAGES}/${bot.bot.username}") { json(alice, message) }.status)

        harness.client.post("${Api.MESSAGES}/${bob.username}") { json(alice, message) }
        harness.client.post("${Api.MESSAGES}/${mallory.username}") { json(alice, message.copy(clientId = UUID.randomUUID().toString())) }
        val bobId = harness.accountId(bob)
        val malloryId = harness.accountId(mallory)
        val queued = harness.services.messages.queued(bobId, 0, 10).single()
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(mallory.token) }) {
            val own = (nextFrame() as ServerFrame.Deliver).envelope.serverId
            listOf(queued.serverId, own).forEach { id ->
                send(Frame.Text(BleskJson.encodeToString(ClientFrame.serializer(), ClientFrame.Ack(id))))
            }
            // Frames are handled in order, so once the own ack has landed the foreign one has been tried.
            eventually { assertEquals(emptyList<Envelope>(), harness.services.messages.queued(malloryId, 0, 10)) }
        }
        assertEquals(listOf(queued), harness.services.messages.queued(bobId, 0, 10))
    }

    @Test
    fun `only the uploader touches an unfinished upload`() = serverTest { harness ->
        val alice = harness.register()
        val mallory = harness.register()
        val size = Api.CHUNK_BYTES + 10L
        val started = harness.client.post(Api.UPLOADS) { json(alice, UploadStart(size)) }.body<UploadStarted>()
        val chunk = ByteArray(Api.CHUNK_BYTES)
        val tail = ByteArray(10)
        val base = "${Api.ATTACHMENTS}/${started.id}"
        suspend fun putChunk(user: TestUser, n: Int, bytes: ByteArray) = harness.client.put("$base/chunks/$n") {
            bearerAuth(user.token)
            setBody(bytes)
        }

        assertEquals(HttpStatusCode.Forbidden, putChunk(mallory, 0, chunk).status)
        assertEquals(HttpStatusCode.NoContent, putChunk(alice, 0, chunk).status)
        assertEquals(HttpStatusCode.NoContent, putChunk(alice, 1, tail).status)
        assertEquals(HttpStatusCode.Forbidden, harness.client.get("$base/chunks") { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.Forbidden, harness.client.post("$base/complete") { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get(base) { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.OK, harness.client.post("$base/complete") { bearerAuth(alice.token) }.status)
        // Finished, it can be read by whoever holds the id, but no longer rewritten even by its uploader.
        assertEquals(HttpStatusCode.BadRequest, putChunk(alice, 1, tail).status)
        assertEquals(HttpStatusCode.OK, harness.client.get(base) { bearerAuth(mallory.token) }.status)

        val single = harness.client.post(Api.ATTACHMENTS) {
            bearerAuth(alice.token)
            contentType(ContentType.Application.OctetStream)
            setBody(ByteArray(10))
        }.body<AttachmentUploaded>()
        assertEquals(HttpStatusCode.NotFound, harness.client.put("${Api.ATTACHMENTS}/${single.id}/chunks/0") {
            bearerAuth(mallory.token)
            setBody(tail)
        }.status)
    }

    @Test
    fun `an unfinished upload cannot be attached to a post`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val alice = harness.register()
        val channel = spaces.create(alice, SpaceKind.Channel)
        val started = harness.client.post(Api.UPLOADS) { json(alice, UploadStart(100)) }.body<UploadStarted>()
        val pointer = AttachmentPointer(id = started.id, key = "", kind = "file", size = 100)
        val response = spaces.post(alice, channel.handle, NewPost(clientId = "p-1", text = "", attachment = pointer))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("unknown_attachment", response.body<ErrorBody>().code)
    }

    @Test
    fun `plain members and outsiders cannot manage a space`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val outsider = harness.register()
        val room = spaces.create(owner, SpaceKind.Room)
        spaces.join(member, room.handle)
        val path = "${Spaces.PATH}/${room.handle}"

        listOf(member to "forbidden", outsider to "not_member").forEach { (user, code) ->
            val refusals = listOf(
                harness.client.patch(path) { json(user, UpdateSpace(title = "Захвачено")) },
                harness.call(HttpMethod.Delete, path) { bearerAuth(user.token) },
                harness.client.post("$path/roles") { json(user, RoleChange(user.username, SpaceRole.Admin)) },
                spaces.ban(user, room.handle, owner.username),
                spaces.unban(user, room.handle, owner.username),
                harness.client.post("$path/channels") { json(user, CreateSpaceChannel("свой")) },
                harness.call(HttpMethod.Delete, "$path/channels/general") { bearerAuth(user.token) },
            )
            refusals.forEachIndexed { index, response ->
                assertEquals("${user.username} #$index", HttpStatusCode.Forbidden, response.status)
                assertEquals("${user.username} #$index", code, response.body<ErrorBody>().code)
            }
        }
        val members = harness.client.get("$path/members") { bearerAuth(outsider.token) }
        assertEquals(HttpStatusCode.Forbidden, members.status)
        assertEquals(room.title, spaces.info(owner, room.handle).title)
        assertEquals(listOf("general"), spaces.info(owner, room.handle).channels.map { it.id })
    }

    @Test
    fun `an admin cannot act on the owner or another admin`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val admin = harness.register()
        val peer = harness.register()
        val room = spaces.create(owner, SpaceKind.Room)
        val path = "${Spaces.PATH}/${room.handle}"
        listOf(admin, peer).forEach { user ->
            spaces.join(user, room.handle)
            harness.client.post("$path/roles") { json(owner, RoleChange(user.username, SpaceRole.Admin)) }
        }

        suspend fun setRole(target: TestUser, role: SpaceRole) =
            harness.client.post("$path/roles") { json(admin, RoleChange(target.username, role)) }.status
        assertEquals(HttpStatusCode.Forbidden, harness.call(HttpMethod.Delete, path) { bearerAuth(admin.token) }.status)
        assertEquals(HttpStatusCode.Forbidden, setRole(peer, SpaceRole.Member))
        assertEquals(HttpStatusCode.Forbidden, setRole(owner, SpaceRole.Member))
        assertEquals(HttpStatusCode.Forbidden, setRole(admin, SpaceRole.Member))
        assertEquals(HttpStatusCode.BadRequest, setRole(admin, SpaceRole.Owner))
        assertEquals(HttpStatusCode.Forbidden, spaces.ban(admin, room.handle, peer.username).status)
        assertEquals(HttpStatusCode.Forbidden, spaces.ban(admin, room.handle, owner.username).status)
        assertEquals(HttpStatusCode.Forbidden, spaces.ban(owner, room.handle, owner.username).status)

        val ownerPost = spaces.post(owner, room.handle, "от владельца", channel = "general").body<Post>()
        val peerPost = spaces.post(peer, room.handle, "от админа", channel = "general").body<Post>()
        assertEquals(HttpStatusCode.Forbidden, deletePost(harness, admin, ownerPost).status)
        assertEquals(HttpStatusCode.Forbidden, deletePost(harness, admin, peerPost).status)
        assertEquals(HttpStatusCode.NoContent, deletePost(harness, owner, peerPost).status)
        val members = harness.client.get("$path/members") { bearerAuth(owner.token) }.body<List<SpaceMember>>()
        val roles = members.associate { it.username to it.role }
        assertEquals(mapOf(owner.username to SpaceRole.Owner, admin.username to SpaceRole.Admin, peer.username to SpaceRole.Admin), roles)
    }

    @Test
    fun `a banned person cannot rejoin, post or edit what they wrote`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val room = spaces.create(owner, SpaceKind.Room)
        spaces.join(member, room.handle)
        val post = spaces.post(member, room.handle, "до бана", channel = "general").body<Post>()
        assertEquals(HttpStatusCode.NoContent, spaces.ban(owner, room.handle, member.username).status)

        val join = spaces.join(member, room.handle)
        assertEquals(HttpStatusCode.Forbidden, join.status)
        assertEquals("banned", join.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.Forbidden, spaces.post(member, room.handle, "после бана", channel = "general").status)
        assertEquals(HttpStatusCode.Forbidden, editPost(harness, member, post, "реклама").status)
        val members = harness.client.get("${Spaces.PATH}/${room.handle}/members") { bearerAuth(member.token) }
        assertEquals(HttpStatusCode.Forbidden, members.status)
        // Taking back their own words stays possible.
        assertEquals(HttpStatusCode.NoContent, deletePost(harness, member, post).status)
    }

    @Test
    fun `a demoted channel admin no longer edits under the channel's name`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val editor = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        val path = "${Spaces.PATH}/${channel.handle}"
        spaces.join(editor, channel.handle)
        harness.client.post("$path/roles") { json(owner, RoleChange(editor.username, SpaceRole.Admin)) }
        val post = spaces.post(editor, channel.handle, "новость").body<Post>()
        harness.client.post("$path/roles") { json(owner, RoleChange(editor.username, SpaceRole.Member)) }

        assertEquals(HttpStatusCode.Forbidden, spaces.post(editor, channel.handle, "ещё").status)
        assertEquals(HttpStatusCode.Forbidden, editPost(harness, editor, post, "подмена").status)
        assertEquals(HttpStatusCode.Forbidden, editPost(harness, owner, post, "чужое").status)
    }

    @Test
    fun `only the owner of a bot renews its token or deletes it`() = serverTest { harness ->
        val owner = harness.register()
        val mallory = harness.register()
        val bot = harness.createBot(owner)
        val path = Bots.userPath(bot.bot.username)
        assertEquals(HttpStatusCode.Forbidden, harness.client.post("$path/token") { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.Forbidden, harness.call(HttpMethod.Delete, path) { bearerAuth(mallory.token) }.status)
        assertEquals(HttpStatusCode.OK, harness.botGet(bot.token, "getMe").status)
        assertEquals(emptyList<BotInfo>(), harness.client.get("${Bots.PATH}/mine") { bearerAuth(mallory.token) }.body<List<BotInfo>>())
    }

    @Test
    fun `a bot reaches only its own chats, messages and button presses`() = serverTest { harness ->
        val bot = harness.createBot(harness.register())
        val other = harness.createBot(harness.register())
        val alice = harness.register()
        val stranger = harness.register()
        harness.writeToBot(alice, bot.bot.username, text = "/start")
        val buttons = listOf(listOf(InlineButton("Да", data = "y")))
        val sent = harness.botPost(bot.token, "sendMessage", SendMessage(alice.username, "?", buttons)).body<SentMessage>()
        harness.writeToBot(alice, bot.bot.username, callback = CallbackPress(sent.messageId, "y"))
        val press = checkNotNull(harness.getUpdates(bot.token).last().callback)

        assertEquals(emptyList<Update>(), harness.getUpdates(other.token))
        assertEquals(HttpStatusCode.Forbidden, harness.botPost(other.token, "sendMessage", SendMessage(alice.username, "спам")).status)
        assertEquals(HttpStatusCode.Forbidden, harness.botPost(bot.token, "sendMessage", SendMessage(stranger.username, "спам")).status)
        val foreignEdit = harness.botPost(other.token, "editMessage", EditMessage(alice.username, sent.messageId, "!"))
        assertEquals(HttpStatusCode.Forbidden, foreignEdit.status)
        assertEquals(HttpStatusCode.NotFound, harness.botPost(other.token, "answerCallback", AnswerCallback(press.id, "!")).status)
        // A person presses buttons only under messages the bot sent to them.
        harness.writeToBot(stranger, bot.bot.username, text = "/start")
        val foreignPress = harness.writeToBot(stranger, bot.bot.username, callback = CallbackPress(sent.messageId, "y"))
        assertEquals(HttpStatusCode.NotFound, foreignPress.status)
    }

    @Test
    fun `a call token is issued only in the caller's own name`() = serverTest(liveKit = LiveKit) { harness ->
        val alice = harness.register()
        // Who else is in the room is unknown to the server: rooms are end-to-end encrypted and their media
        // key travels only between members, so a stranger who guesses a room name gets ciphertext alone.
        val response = harness.client.post(Calls.TOKEN) { json(alice, CallTokenRequest(Calls.directRoom(UUID.randomUUID().toString()))) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(alice.username, JWT.decode(response.body<CallToken>().token).subject)
    }

    private suspend fun editPost(harness: Harness, user: TestUser, post: Post, text: String): HttpResponse =
        harness.client.patch("${Spaces.PATH}/${post.space}/posts/${post.id}") { json(user, EditPost(text)) }

    private suspend fun deletePost(harness: Harness, user: TestUser, post: Post): HttpResponse =
        harness.call(HttpMethod.Delete, "${Spaces.PATH}/${post.space}/posts/${post.id}") { bearerAuth(user.token) }

    private companion object {
        const val SOME_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val SOME_SPACE = "${Spaces.PATH}/somespace"
        val SomeBot = Bots.userPath("some${Bots.SUFFIX}")

        /** Every route that needs a person's token; a new route belongs here. */
        val AccountRoutes = listOf(
            HttpMethod.Get to "${Api.ACCOUNTS}/someone",
            HttpMethod.Delete to Api.ACCOUNT,
            HttpMethod.Put to Api.RECOVERY,
            HttpMethod.Put to Api.DISCOVERY,
            HttpMethod.Put to Api.KEYS,
            HttpMethod.Get to Api.KEY_STATUS,
            HttpMethod.Get to "${Api.KEYS}/someone",
            HttpMethod.Post to "${Api.MESSAGES}/someone",
            HttpMethod.Post to Api.ATTACHMENTS,
            HttpMethod.Post to Api.UPLOADS,
            HttpMethod.Put to "${Api.ATTACHMENTS}/$SOME_ID/chunks/0",
            HttpMethod.Get to "${Api.ATTACHMENTS}/$SOME_ID/chunks",
            HttpMethod.Post to "${Api.ATTACHMENTS}/$SOME_ID/complete",
            HttpMethod.Get to "${Api.ATTACHMENTS}/$SOME_ID",
            HttpMethod.Post to Spaces.PATH,
            HttpMethod.Get to Spaces.PATH,
            HttpMethod.Get to "${Spaces.PATH}/mine",
            HttpMethod.Get to SOME_SPACE,
            HttpMethod.Patch to SOME_SPACE,
            HttpMethod.Delete to SOME_SPACE,
            HttpMethod.Post to "$SOME_SPACE/join",
            HttpMethod.Post to "$SOME_SPACE/leave",
            HttpMethod.Get to "$SOME_SPACE/members",
            HttpMethod.Post to "$SOME_SPACE/roles",
            HttpMethod.Post to "$SOME_SPACE/ban/someone",
            HttpMethod.Delete to "$SOME_SPACE/ban/someone",
            HttpMethod.Post to "$SOME_SPACE/channels",
            HttpMethod.Delete to "$SOME_SPACE/channels/general",
            HttpMethod.Post to "$SOME_SPACE/posts",
            HttpMethod.Get to "$SOME_SPACE/posts",
            HttpMethod.Patch to "$SOME_SPACE/posts/1",
            HttpMethod.Delete to "$SOME_SPACE/posts/1",
            HttpMethod.Post to Calls.TOKEN,
            HttpMethod.Post to Bots.PATH,
            HttpMethod.Get to "${Bots.PATH}/mine",
            HttpMethod.Get to SomeBot,
            HttpMethod.Post to "$SomeBot/token",
            HttpMethod.Delete to SomeBot,
            HttpMethod.Post to "$SomeBot/updates",
            HttpMethod.Get to Api.INVITES,
            HttpMethod.Post to Api.INVITES,
        )

        /** Every Bot API method. */
        val BotApiRoutes = listOf(
            HttpMethod.Get to "getMe",
            HttpMethod.Get to "getUpdates",
            HttpMethod.Post to "sendMessage",
            HttpMethod.Post to "editMessage",
            HttpMethod.Post to "answerCallback",
            HttpMethod.Post to "setWebhook",
            HttpMethod.Post to "deleteWebhook",
            HttpMethod.Post to "setCommands",
        )

        val LiveKit = LiveKitConfig(
            publicUrl = "wss://blesk.example/livekit",
            apiKey = "APItest",
            apiSecret = "s".repeat(64),
        )
    }
}

private suspend fun Harness.call(method: HttpMethod, path: String, configure: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
    client.request(path) {
        this.method = method
        configure()
    }

private inline fun <reified T> HttpRequestBuilder.json(user: TestUser, body: T) {
    bearerAuth(user.token)
    contentType(ContentType.Application.Json)
    setBody(body)
}
