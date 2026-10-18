package app.blesk.core.network

import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.OutgoingMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BleskApiErrorsTest {
    private val body = Json.encodeToString(ErrorBody.serializer(), ErrorBody("rate_limited"))

    private fun refusal(vararg headers: Pair<String, String>): ApiException {
        val all = (headers.toList() + (HttpHeaders.ContentType to "application/json")).map { (name, value) -> name to listOf(value) }
        val engine = MockEngine { respond(body, HttpStatusCode.TooManyRequests, headersOf(*all.toTypedArray())) }
        val api = BleskApi(ServerAddress.DEFAULT, engine)
        return assertThrows(ApiException::class.java) { runBlocking { api.send("bob", OutgoingMessage("id", 1, "body")) } }
    }

    @Test
    fun `a pause named in seconds comes with the refusal`() {
        val error = refusal(HttpHeaders.RetryAfter to "30")
        assertEquals(429, error.status)
        assertEquals("rate_limited", error.code)
        assertEquals(30_000L, error.retryAfterMillis)
    }

    @Test
    fun `no pause, or one given as a date, leaves the choice to the client`() {
        assertNull(refusal().retryAfterMillis)
        assertNull(refusal(HttpHeaders.RetryAfter to "Wed, 21 Oct 2026 07:28:00 GMT").retryAfterMillis)
        assertNull(refusal(HttpHeaders.RetryAfter to "-5").retryAfterMillis)
    }
}
