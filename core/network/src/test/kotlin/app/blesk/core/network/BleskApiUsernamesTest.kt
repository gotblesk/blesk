package app.blesk.core.network

import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameVerdict
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BleskApiUsernamesTest {
    private val json = Json { encodeDefaults = true }
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(signedIn: Boolean = false, answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BleskApi(ServerAddress.DEFAULT, MockEngine { request -> requests += request; answer(request) })
            .apply { if (signedIn) token = "t" }

    private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK, vararg extra: Pair<String, String>) =
        respond(body, status, headersOf(*(extra.toList() + (HttpHeaders.ContentType to "application/json")).map(::header).toTypedArray()))

    private fun header(pair: Pair<String, String>) = pair.first to listOf(pair.second)

    private fun MockRequestHandleScope.refuse(code: String, status: HttpStatusCode, vararg extra: Pair<String, String>) =
        reply(json.encodeToString(ErrorBody.serializer(), ErrorBody(code)), status, *extra)

    @Test
    fun `a newer server's answer comes through whole`() = runBlocking {
        val answer = UsernameCheck(false, UsernameVerdict.TAKEN, "egor", listOf("egor7", "egor.15", "egor_42"))
        val api = api { reply(json.encodeToString(UsernameCheck.serializer(), answer)) }
        assertEquals(answer, api.checkUsername("Egor"))
    }

    @Test
    fun `an older server's yes or no reads as free or taken`() = runBlocking {
        assertEquals(UsernameCheck(false, UsernameVerdict.TAKEN, "anya"), api { reply("""{"available":false}""") }.checkUsername("anya"))
        assertEquals(UsernameCheck(true, UsernameVerdict.FREE, "vera"), api { reply("""{"available":true}""") }.checkUsername("vera"))
    }

    @Test
    fun `whatever was typed goes out as one path segment`() = runBlocking {
        api { reply("""{"available":false}""") }.checkUsername("a b/c")
        assertEquals("/v1/usernames/a%20b%2Fc", requests.single().url.encodedPath)
    }

    @Test
    fun `the check carries the person's token`() = runBlocking {
        api(signedIn = true) { reply("""{"available":true}""") }.checkUsername("vera")
        assertEquals("Bearer t", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a token the server no longer takes is dropped and the check asked again`() = runBlocking {
        val api = api(signedIn = true) { request ->
            when (request.headers[HttpHeaders.Authorization]) {
                null -> reply("""{"available":true}""")
                else -> refuse("unauthorized", HttpStatusCode.Unauthorized)
            }
        }
        assertEquals(true, api.checkUsername("vera").available)
        assertEquals(listOf("Bearer t", null), requests.map { it.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun `too many checks say how long to wait`() {
        val api = api { refuse("rate_limited", HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "4") }
        val error = assertThrows(ApiException::class.java) { runBlocking { api.checkUsername("vera") } }
        assertEquals(429, error.status)
        assertEquals(4_000L, error.retryAfterMillis)
        assertEquals(1, requests.size)
    }
}
