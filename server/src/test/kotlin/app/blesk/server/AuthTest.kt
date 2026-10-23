package app.blesk.server

import app.blesk.server.auth.Registration
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.KeyStatus
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.SendMessage
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.util.UUID
import kotlin.random.Random

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
    fun `every request but health is rate limited per address`() =
        serverTest(requestRateLimit = RateLimitConfig(capacity = 2, refillPerMinute = 1)) { harness ->
            repeat(2) { assertEquals(HttpStatusCode.OK, harness.client.get(Api.INFO).status) }
            val limited = harness.client.get(Api.INFO)
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("60", limited.headers[HttpHeaders.RetryAfter])
            assertEquals(HttpStatusCode.TooManyRequests, harness.client.get(Api.KEY_STATUS).status)
            // Monitors keep their answer whatever the load.
            assertEquals(HttpStatusCode.OK, harness.client.get(Api.HEALTH).status)
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

    @Test
    fun `an account whose name became reserved still signs in and is reached`() = serverTest { harness ->
        val name = "support" + Random.nextInt(100_000, 999_999)
        val identity = IdentityKeyPair.generate()
        val created = harness.services.accounts.create(name, identity.publicKey.serialize(), 1, harness.services.now())
        assertEquals(Registration.Created, created)
        val person = TestUser(name, identity, harness.login(name, identity))
        val bob = harness.register()

        assertEquals(HttpStatusCode.OK, harness.client.get("${Api.ACCOUNTS}/$name") { bearerAuth(bob.token) }.status)
        val message = OutgoingMessage(UUID.randomUUID().toString(), EnvelopeType.WHISPER, Random.nextBytes(32).b64())
        val sent = harness.client.post("${Api.MESSAGES}/$name") {
            bearerAuth(bob.token)
            contentType(ContentType.Application.Json)
            setBody(message)
        }
        assertEquals(HttpStatusCode.OK, sent.status)
        // The name is found; only the keys are missing, since none were uploaded.
        val keys = harness.client.get("${Api.KEYS}/$name") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.NotFound, keys.status)
        assertEquals("no_keys", keys.body<ErrorBody>().code)

        val bot = harness.createBot(bob)
        assertEquals(HttpStatusCode.NoContent, harness.writeToBot(person, bot.bot.username, text = "/start").status)
        assertEquals(HttpStatusCode.OK, harness.botPost(bot.token, "sendMessage", SendMessage(name, "Привет!")).status)
    }
}
