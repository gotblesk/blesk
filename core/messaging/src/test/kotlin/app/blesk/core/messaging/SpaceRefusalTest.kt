package app.blesk.core.messaging

import app.blesk.core.data.SpaceResult
import app.blesk.core.network.ApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceRefusalTest {
    @Test
    fun aHandleTheServerKeepsBackIsAnInvalidHandle() {
        assertEquals(SpaceResult.HandleInvalid, ApiException(400, "invalid_handle").spaceResult())
    }

    @Test
    fun aTakenHandleIsTakenAndAnythingElseIsAFailure() {
        assertEquals(SpaceResult.HandleTaken, ApiException(409, "handle_taken").spaceResult())
        assertEquals(SpaceResult.Failed, ApiException(429, "rate_limited", retryAfterMillis = 3_000).spaceResult())
        assertEquals(SpaceResult.Failed, ApiException(500, "internal").spaceResult())
    }
}
