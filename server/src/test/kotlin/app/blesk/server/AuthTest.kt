package app.blesk.server

import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.KeyStatus
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair

class AuthTest {
    @Test
    fun `token from a signed challenge authenticates requests`() = serverTest { harness ->
        val alice = harness.register()
        val response = harness.client.get(Api.KEY_STATUS) { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(KeyStatus(0, 0), response.body<KeyStatus>())
    }

    @Test
    fun `a nonce can be redeemed only once`() = serverTest { harness ->
        val alice = harness.register()
        val nonce = harness.challenge(alice.username).nonce
        assertEquals(HttpStatusCode.OK, harness.tokenRaw(alice.username, nonce, alice.identity).status)

        val reused = harness.tokenRaw(alice.username, nonce, alice.identity)
        assertEquals(HttpStatusCode.Unauthorized, reused.status)
        assertEquals("auth_failed", reused.body<ErrorBody>().code)
    }

    @Test
    fun `a signature by another key is rejected`() = serverTest { harness ->
        val alice = harness.register()
        val nonce = harness.challenge(alice.username).nonce
        val response = harness.tokenRaw(alice.username, nonce, IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("auth_failed", response.body<ErrorBody>().code)
    }

    @Test
    fun `unknown usernames still get a challenge but no token`() = serverTest { harness ->
        val username = randomUsername()
        val challenge = harness.challenge(username)
        assertTrue(challenge.expiresAtMillis > System.currentTimeMillis())
        assertEquals(HttpStatusCode.Unauthorized, harness.tokenRaw(username, challenge.nonce, IdentityKeyPair.generate()).status)
    }

    @Test
    fun `protected routes need a valid token`() = serverTest { harness ->
        val missing = harness.client.get(Api.KEY_STATUS)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("unauthorized", missing.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.Unauthorized, harness.client.get(Api.KEY_STATUS) { bearerAuth("forged") }.status)
        assertEquals(HttpStatusCode.Unauthorized, harness.client.get("${Api.ATTACHMENTS}/AAAAAAAAAAAAAAAAAAAAAA").status)
    }

    @Test
    fun `auth endpoints are rate limited per address`() = serverTest(RateLimitConfig(capacity = 3, refillPerMinute = 1)) { harness ->
        val username = randomUsername()
        repeat(3) { harness.challenge(username) }
        val limited = harness.client.post(Api.CHALLENGE) {
            contentType(ContentType.Application.Json)
            setBody(ChallengeRequest(username))
        }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", limited.body<ErrorBody>().code)
    }
}
