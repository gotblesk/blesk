package app.blesk.server

import app.blesk.shared.protocol.CreateSpace
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.Spaces
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import java.util.UUID

/** Spaces calls used across the spaces tests. */
class SpaceClient(private val harness: Harness) {
    private val client get() = harness.client

    suspend fun createRaw(user: TestUser, handle: String, kind: SpaceKind, title: String = "Title $handle"): HttpResponse =
        client.post(Spaces.PATH) {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSpace(handle, kind, title))
        }

    suspend fun create(user: TestUser, kind: SpaceKind, handle: String = randomUsername()): SpaceInfo {
        val response = createRaw(user, handle, kind)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    suspend fun join(user: TestUser, handle: String): HttpResponse = client.post("${Spaces.PATH}/$handle/join") { bearerAuth(user.token) }

    suspend fun leave(user: TestUser, handle: String): HttpResponse = client.post("${Spaces.PATH}/$handle/leave") { bearerAuth(user.token) }

    suspend fun info(user: TestUser, handle: String): SpaceInfo = client.get("${Spaces.PATH}/$handle") { bearerAuth(user.token) }.body()

    suspend fun post(user: TestUser, handle: String, text: String, channel: String = NewPost.MAIN_CHANNEL, clientId: String = uuid()) =
        post(user, handle, NewPost(clientId = clientId, text = text, channel = channel))

    suspend fun post(user: TestUser, handle: String, post: NewPost): HttpResponse = client.post("${Spaces.PATH}/$handle/posts") {
        bearerAuth(user.token)
        contentType(ContentType.Application.Json)
        setBody(post)
    }

    suspend fun ban(moderator: TestUser, handle: String, username: String): HttpResponse =
        client.post("${Spaces.PATH}/$handle/ban/$username") { bearerAuth(moderator.token) }

    suspend fun unban(moderator: TestUser, handle: String, username: String): HttpResponse =
        client.delete("${Spaces.PATH}/$handle/ban/$username") { bearerAuth(moderator.token) }

    private fun uuid() = UUID.randomUUID().toString()
}
