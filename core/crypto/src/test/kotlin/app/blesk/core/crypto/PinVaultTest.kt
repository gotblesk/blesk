package app.blesk.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class PinVaultTest {

    @Test
    fun `four mistakes are free then 30 seconds, a minute, five minutes and a quarter of an hour`() {
        assertEquals(0L, PinVault.backoff(1))
        assertEquals(0L, PinVault.backoff(4))
        assertEquals(30_000L, PinVault.backoff(5))
        assertEquals(60_000L, PinVault.backoff(6))
        assertEquals(300_000L, PinVault.backoff(7))
        assertEquals(900_000L, PinVault.backoff(8))
        assertEquals(900_000L, PinVault.backoff(40))
    }
}
