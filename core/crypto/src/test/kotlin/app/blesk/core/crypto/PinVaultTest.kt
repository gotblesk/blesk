package app.blesk.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class PinVaultTest {

    @Test
    fun `first mistakes are free then the wait doubles up to an hour`() {
        assertEquals(0L, PinVault.backoff(1))
        assertEquals(0L, PinVault.backoff(4))
        assertEquals(30_000L, PinVault.backoff(5))
        assertEquals(60_000L, PinVault.backoff(6))
        assertEquals(3_600_000L, PinVault.backoff(40))
    }
}
