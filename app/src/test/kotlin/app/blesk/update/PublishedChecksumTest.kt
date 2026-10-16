package app.blesk.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedChecksumTest {
    private val sha = "0123456789abcdef".repeat(4)

    @Test
    fun readsTheLineTheWorkflowWrites() {
        val notes = "## 1.0 RC12\n\n- что нового\n\nSHA-256 APK: `${sha.uppercase()}`\nКлюч подписи (SHA-256): `$sha`"
        assertEquals(sha, publishedChecksum(notes))
    }

    @Test
    fun withoutTheLineThereIsNothingToTrust() {
        assertNull(publishedChecksum("Ключ подписи (SHA-256): `$sha`"))
        assertNull(publishedChecksum("SHA-256 APK: `1234`"))
    }
}
