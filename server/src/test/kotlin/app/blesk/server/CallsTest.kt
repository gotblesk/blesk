package app.blesk.server

import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.CallToken
import app.blesk.shared.protocol.CallTokenRequest
import app.blesk.shared.protocol.Calls
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.ServerInfo
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.SignatureVerificationException
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class CallsTest {
    private val liveKit = LiveKitConfig(
        publicUrl = "wss://blesk.example/livekit",
        apiKey = "APItest",
        apiSecret = "s".repeat(64),
    )

    @Test
    fun `issues a LiveKit token for one room`() = serverTest(liveKit = liveKit) { harness ->
        val alice = harness.register()
        val room = Calls.directRoom("0f8a6c1e-3b9d-4c55-9a3e-2d7c1b4e8f60")

        val response = harness.requestToken(alice.token, room)
        assertEquals(HttpStatusCode.OK, response.status)
        val issued = response.body<CallToken>()
        assertEquals(liveKit.publicUrl, issued.url)

        val jwt = JWT.require(Algorithm.HMAC256(liveKit.apiSecret)).withIssuer(liveKit.apiKey).build().verify(issued.token)
        assertEquals("HS256", jwt.algorithm)
        assertEquals(alice.username, jwt.subject)
        assertEquals(Duration.ofHours(6), Duration.between(jwt.notBeforeAsInstant, jwt.expiresAtAsInstant))
        val video = jwt.getClaim("video").asMap()
        assertEquals(room, video["room"])
        listOf("roomJoin", "canPublish", "canSubscribe", "canPublishData").forEach { grant -> assertEquals(grant, true, video[grant]) }
        assertThrows(SignatureVerificationException::class.java) {
            JWT.require(Algorithm.HMAC256("x".repeat(64))).build().verify(issued.token)
        }
    }

    @Test
    fun `rejects an invalid room`() = serverTest(liveKit = liveKit) { harness ->
        val alice = harness.register()
        listOf("", "room with spaces", "a/b", "x".repeat(129)).forEach { room ->
            val response = harness.requestToken(alice.token, room)
            assertEquals(room, HttpStatusCode.BadRequest, response.status)
            assertEquals("invalid_room", response.body<ErrorBody>().code)
        }
    }

    @Test
    fun `requires a session`() = serverTest(liveKit = liveKit) { harness ->
        assertEquals(HttpStatusCode.Unauthorized, harness.requestToken(token = null, room = "call-1").status)
        assertEquals(HttpStatusCode.Unauthorized, harness.requestToken(token = "not-a-token", room = "call-1").status)
    }

    @Test
    fun `answers 503 when LiveKit is not configured`() = serverTest(liveKit = null) { harness ->
        val alice = harness.register()
        val response = harness.requestToken(alice.token, "call-1")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("calls_unavailable", response.body<ErrorBody>().code)
    }

    @Test
    fun `limits tokens per account`() = serverTest(
        liveKit = liveKit,
        callRateLimit = RateLimitConfig(capacity = 2, refillPerMinute = 1),
    ) { harness ->
        val alice = harness.register()
        repeat(2) { assertEquals(HttpStatusCode.OK, harness.requestToken(alice.token, "call-1").status) }
        val limited = harness.requestToken(alice.token, "call-1")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", limited.body<ErrorBody>().code)
    }

    @Test
    fun `info tells whether calls are available`() {
        serverTest(liveKit = liveKit) { harness -> assertTrue(harness.client.get(Api.INFO).body<ServerInfo>().calls) }
        serverTest(liveKit = null) { harness -> assertFalse(harness.client.get(Api.INFO).body<ServerInfo>().calls) }
    }

    @Test
    fun `reads LiveKit settings from the environment`() {
        val base = mapOf("BLESK_STORAGE" to "fs")
        assertEquals(null, Config.fromEnvironment(base).liveKit)
        val full = base + mapOf(
            "LIVEKIT_PUBLIC_URL" to liveKit.publicUrl,
            "LIVEKIT_API_KEY" to liveKit.apiKey,
            "LIVEKIT_API_SECRET" to liveKit.apiSecret,
        )
        assertEquals(liveKit, Config.fromEnvironment(full).liveKit)
        assertFalse(liveKit.toString().contains(liveKit.apiSecret))
        assertThrows(IllegalArgumentException::class.java) { Config.fromEnvironment(full - "LIVEKIT_API_SECRET") }
    }

    private suspend fun Harness.requestToken(token: String?, room: String): HttpResponse = client.post(Calls.TOKEN) {
        token?.let(::bearerAuth)
        contentType(ContentType.Application.Json)
        setBody(CallTokenRequest(room))
    }
}
