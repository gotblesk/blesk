package app.blesk.server

import app.blesk.server.http.RateLimiter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test
    fun `buckets empty, refill over time and are kept per address`() {
        var now = 0L
        val limiter = RateLimiter(RateLimitConfig(capacity = 2, refillPerMinute = 60)) { now }
        assertTrue(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))

        now += 1_000_000_000L
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
    }
}
