package app.blesk.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitesTest {
    private val code = "abcdefghjk23"

    @Test
    fun `a code is read from itself, its link and its link without the scheme`() {
        assertEquals(code, Invites.parse(code))
        assertEquals(code, Invites.parse("  ${code.uppercase()} "))
        assertEquals(code, Invites.parse(Invites.link(code)))
        assertEquals(code, Invites.parse("blesk.su/i/$code"))
        assertEquals(code, Invites.parse("https://www.blesk.su/i/$code/"))
        assertEquals(code, Invites.parse("https://blesk.su/i/$code?from=qr"))
    }

    @Test
    fun `anything else is not a code`() {
        assertNull(Invites.parse(""))
        assertNull(Invites.parse("abcdefghjk2"))
        assertNull(Invites.parse("abcdefghjk231"))
        assertNull(Invites.parse("abcdefghjk10"))
        assertNull(Invites.parse("https://evil.example/i/$code"))
        assertNull(Invites.parse("https://blesk.su/c/$code"))
        assertNull(Invites.parse("https://blesk.su.evil.example/i/$code"))
    }

    @Test
    fun `codes avoid look-alike characters`() {
        listOf('0', '1', 'i', 'l', 'o').forEach { assertFalse(it in Invites.ALPHABET) }
        assertTrue(Invites.isValid(code))
        assertEquals("https://blesk.su/i/$code", Invites.link(code))
    }
}
