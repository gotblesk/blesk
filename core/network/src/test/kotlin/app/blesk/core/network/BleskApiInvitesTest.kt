package app.blesk.core.network

import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList
import app.blesk.shared.protocol.RegisterRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BleskApiInvitesTest {
    private val json = Json { encodeDefaults = true }
    private val requests = mutableListOf<HttpRequestData>()
    private val invite = InviteInfo("abcdefghjkmn", createdAtMillis = 1, expiresAtMillis = 2)

    private fun api(answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BleskApi(ServerAddress.DEFAULT, MockEngine { request -> requests += request; answer(request) }).apply { token = "t" }

    private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `invites are listed and made with the person's token`() = runBlocking {
        val api = api { request ->
            when (request.method) {
                HttpMethod.Get -> reply(json.encodeToString(InviteList.serializer(), InviteList(listOf(invite), left = 4)))
                else -> reply(json.encodeToString(InviteInfo.serializer(), invite), HttpStatusCode.Created)
            }
        }
        assertEquals(InviteList(listOf(invite), 4), api.listInvites())
        assertEquals(invite, api.createInvite())
        assertEquals(listOf("https://api.blesk.su${Api.INVITES}"), requests.map { it.url.toString() }.distinct())
        assertEquals(listOf("Bearer t", "Bearer t"), requests.map { it.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun `the limit comes back as its error code`() {
        val limit = json.encodeToString(ErrorBody.serializer(), ErrorBody("invite_limit"))
        val api = api { reply(limit, HttpStatusCode.Conflict) }
        val error = assertThrows(ApiException::class.java) { runBlocking { api.createInvite() } }
        assertEquals(409, error.status)
        assertEquals("invite_limit", error.code)
    }

    @Test
    fun `registration carries the invite`() = runBlocking {
        val api = api { reply("""{"username":"anya","identityKey":"k"}""", HttpStatusCode.Created) }
        api.register(RegisterRequest("anya", "k", registrationId = 1, signature = "s", invite = invite.code))
        val body = (requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertEquals(invite.code, json.decodeFromString(RegisterRequest.serializer(), body).invite)
    }
}
