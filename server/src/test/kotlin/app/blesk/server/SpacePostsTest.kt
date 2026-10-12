package app.blesk.server

import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentPointer
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.EditPost
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.Spaces
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SpacePostsTest {
    @Test
    fun `in a channel only the owner and admins post`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        spaces.join(member, channel.handle)

        assertEquals(HttpStatusCode.Forbidden, spaces.post(member, channel.handle, "hi").status)
        val created = spaces.post(owner, channel.handle, "news")
        assertEquals(HttpStatusCode.Created, created.status)
        val post = created.body<Post>()
        assertEquals(channel.handle, post.space)
        assertEquals("main", post.channel)
        assertEquals(owner.username, post.author)
    }

    @Test
    fun `in a room every member posts, outsiders do not`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val outsider = harness.register()
        val room = spaces.create(owner, SpaceKind.Room)
        spaces.join(member, room.handle)

        assertEquals(HttpStatusCode.Created, spaces.post(member, room.handle, "hello", channel = "general").status)
        val notMember = spaces.post(outsider, room.handle, "hello", channel = "general")
        assertEquals(HttpStatusCode.Forbidden, notMember.status)
        assertEquals("not_member", notMember.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.NotFound, spaces.post(member, room.handle, "hello", channel = "nowhere").status)
        val tooLong = spaces.post(member, room.handle, "x".repeat(Spaces.MAX_POST + 1), channel = "general")
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
    }

    @Test
    fun `history is public, newest first and paged`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val reader = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        val ids = (1..5).map { spaces.post(owner, channel.handle, "post $it").body<Post>().id }

        val first = posts(harness, reader, channel.handle, "?limit=2")
        assertEquals(ids.reversed().take(2), first.map { it.id })
        val next = posts(harness, reader, channel.handle, "?channel=main&before=${first.last().id}&limit=50")
        assertEquals(ids.reversed().drop(2), next.map { it.id })
    }

    @Test
    fun `repeating a client id returns the same post`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        val first = spaces.post(owner, channel.handle, "once", clientId = "c-1")
        val retry = spaces.post(owner, channel.handle, "once", clientId = "c-1")
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.OK, retry.status)
        assertEquals(first.body<Post>(), retry.body<Post>())
        assertEquals(1, posts(harness, owner, channel.handle).size)
    }

    @Test
    fun `authors edit, authors and moderators delete`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val other = harness.register()
        val room = spaces.create(owner, SpaceKind.Room)
        spaces.join(member, room.handle)
        spaces.join(other, room.handle)
        val post = spaces.post(member, room.handle, "draft", channel = "general").body<Post>()

        assertEquals(HttpStatusCode.Forbidden, edit(harness, owner, post, "hijacked").status)
        val edited = edit(harness, member, post, "final").body<Post>()
        assertEquals("final", edited.text)
        assertNotNull(edited.editedAtMillis)

        assertEquals(HttpStatusCode.Forbidden, delete(harness, other, post).status)
        assertEquals(HttpStatusCode.NoContent, delete(harness, owner, post).status)
        assertEquals(emptyList<Post>(), posts(harness, member, room.handle, "?channel=general"))

        val own = spaces.post(member, room.handle, "mine", channel = "general").body<Post>()
        assertEquals(HttpStatusCode.NoContent, delete(harness, member, own).status)
    }

    @Test
    fun `members receive new, edited and deleted posts live`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val outsider = harness.register()
        listOf(member, outsider).forEach { harness.uploadKeys(it, harness.keys(it, preKeys = 25, kyberPreKeys = 25)) }
        val room = spaces.create(owner, SpaceKind.Room)
        spaces.join(member, room.handle)

        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(outsider.token) }) {
            val outsiderSocket = this
            assertEquals(ServerFrame.Synced, outsiderSocket.nextFrame())
            harness.client.webSocket(Api.SOCKET, request = { bearerAuth(member.token) }) {
                assertEquals(ServerFrame.Synced, nextFrame())
                val post = spaces.post(owner, room.handle, "live", channel = "general").body<Post>()
                assertEquals(ServerFrame.SpacePost(post), nextFrame())

                val edited = edit(harness, owner, post, "edited").body<Post>()
                assertEquals(ServerFrame.SpacePostChanged(room.handle, post.id, edited), nextFrame())

                delete(harness, owner, post)
                assertEquals(ServerFrame.SpacePostChanged(room.handle, post.id, null), nextFrame())

                // Not a member, so nothing arrived; a direct message is the next frame it sees.
                val dm = harness.client.post("${Api.MESSAGES}/${outsider.username}") {
                    bearerAuth(owner.token)
                    contentType(ContentType.Application.Json)
                    setBody(OutgoingMessage("dm-1", 2, "AAAA"))
                }
                assertEquals(HttpStatusCode.OK, dm.status)
                assertTrue(outsiderSocket.nextFrame() is ServerFrame.Deliver)
            }
        }
    }

    @Test
    fun `attachments of posts outlive the usual retention`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        val upload = harness.client.post(Api.ATTACHMENTS) {
            bearerAuth(owner.token)
            contentType(ContentType.Application.OctetStream)
            setBody(ByteArray(100))
        }.body<AttachmentUploaded>()
        val pointer = AttachmentPointer(id = upload.id, key = "", kind = "image", mime = "image/jpeg", size = 100)
        val post = spaces.post(owner, channel.handle, NewPost(clientId = "a-1", text = "", attachment = pointer)).body<Post>()
        assertEquals(pointer, post.attachment)

        val unknown = spaces.post(owner, channel.handle, NewPost("a-2", "", attachment = pointer.copy(id = "AAAAAAAAAAAAAAAAAAAAAA")))
        assertEquals("unknown_attachment", unknown.body<ErrorBody>().code)

        val farFuture = Instant.now().plusSeconds(400L * 24 * 3600)
        assertTrue(upload.id !in harness.services.attachments.expired(farFuture, farFuture, 10_000).map { it.first })
        delete(harness, owner, post)
        assertTrue(upload.id in harness.services.attachments.expired(farFuture, farFuture, 10_000).map { it.first })
    }

    @Test
    fun `posting is rate limited per account`() = serverTest(postRateLimit = RateLimitConfig(3, refillPerMinute = 1)) { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        repeat(3) { assertEquals(HttpStatusCode.Created, spaces.post(owner, channel.handle, "p$it").status) }
        val limited = spaces.post(owner, channel.handle, "too fast")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", limited.body<ErrorBody>().code)
    }

    @Test
    fun `deleting a space removes its posts`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val channel = spaces.create(owner, SpaceKind.Channel)
        val post = spaces.post(owner, channel.handle, "bye").body<Post>()
        harness.client.delete("${Spaces.PATH}/${channel.handle}") { bearerAuth(owner.token) }
        val remaining = TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM space_posts WHERE id = ?").use { statement ->
                statement.setLong(1, post.id)
                statement.executeQuery().use { rows -> rows.next().let { rows.getInt(1) } }
            }
        }
        assertEquals(0, remaining)
        val gone = harness.client.get("${Spaces.PATH}/${channel.handle}/posts") { bearerAuth(owner.token) }
        assertEquals(HttpStatusCode.NotFound, gone.status)
    }

    private suspend fun posts(harness: Harness, user: TestUser, handle: String, query: String = ""): List<Post> =
        harness.client.get("${Spaces.PATH}/$handle/posts$query") { bearerAuth(user.token) }.body()

    private suspend fun edit(harness: Harness, user: TestUser, post: Post, text: String): HttpResponse =
        harness.client.patch("${Spaces.PATH}/${post.space}/posts/${post.id}") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(EditPost(text))
        }

    private suspend fun delete(harness: Harness, user: TestUser, post: Post): HttpResponse =
        harness.client.delete("${Spaces.PATH}/${post.space}/posts/${post.id}") { bearerAuth(user.token) }
}
