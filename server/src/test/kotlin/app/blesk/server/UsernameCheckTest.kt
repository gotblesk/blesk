package app.blesk.server

import app.blesk.shared.protocol.ErrorBody
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

class UsernameCheckTest {
    @Test
    fun `checks have their own budget, so registration still works`() = serverTest(
        rateLimit = RateLimitConfig(capacity = 3, refillPerMinute = 1),
        usernameCheckRateLimit = RateLimitConfig(capacity = 40, refillPerMinute = 20),
    ) { harness ->
        repeat(40) { harness.checkUsername(randomUsername()) }
        val limited = harness.checkUsernameRaw(randomUsername())
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", limited.body<ErrorBody>().code)
        assertEquals("3", limited.headers[HttpHeaders.RetryAfter])
        // Registering and signing in take all three of the address's attempts, and still have them.
        harness.register()
    }

    @Test
    fun `with a token the budget is the account's`() = serverTest(
        usernameCheckRateLimit = RateLimitConfig(capacity = 1, refillPerMinute = 1),
    ) { harness ->
        val alice = harness.register()
        val bob = harness.register()
        harness.checkUsername(randomUsername())
        assertEquals(HttpStatusCode.TooManyRequests, harness.checkUsernameRaw(randomUsername()).status)

        repeat(30) { harness.checkUsername(randomUsername(), alice.token) }
        val limited = harness.checkUsernameRaw(randomUsername(), alice.token)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("4", limited.headers[HttpHeaders.RetryAfter])
        harness.checkUsername(randomUsername(), bob.token)
    }

    @Test
    fun `the address budget is set apart from registration's`() {
        val base = mapOf("BLESK_STORAGE" to "fs", "BLESK_AUTH_RATE_BURST" to "5")
        val defaults = Config.fromEnvironment(base)
        assertEquals(RateLimitConfig(capacity = 40, refillPerMinute = 20), defaults.usernameCheckRateLimit)
        assertEquals(RateLimitConfig(capacity = 5, refillPerMinute = 30), defaults.authRateLimit)
        val checks = mapOf("BLESK_USERNAME_CHECK_RATE_BURST" to "8", "BLESK_USERNAME_CHECK_RATE_PER_MINUTE" to "4")
        val set = Config.fromEnvironment(base + checks)
        assertEquals(RateLimitConfig(capacity = 8, refillPerMinute = 4), set.usernameCheckRateLimit)
        assertEquals(defaults.authRateLimit, set.authRateLimit)
    }

    @Test
    fun `a token that no longer works is refused, not ignored`() = serverTest { harness ->
        assertEquals(HttpStatusCode.Unauthorized, harness.checkUsernameRaw(randomUsername(), token = "not-a-token").status)
    }
}
