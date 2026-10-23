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

    @Test
    fun `find takes a code or its link like parse`() {
        assertEquals(code, Invites.find(code))
        assertEquals(code, Invites.find(" ${code.uppercase()} "))
        assertEquals(code, Invites.find("https://blesk.su/i/$code"))
        assertEquals(code, Invites.find("www.blesk.su/i/$code/"))
    }

    @Test
    fun `an invite is found in a whole copied message`() {
        assertEquals(code, Invites.find("Приглашаю тебя в blesk: https://blesk.su/i/$code"))
        assertEquals(code, Invites.find("Приглашаю тебя в blesk:\nhttps://blesk.su/i/$code."))
        assertEquals(code, Invites.find("Вот код: «$code», заходи"))
    }

    @Test
    fun `a link wins over a word that only looks like a code`() {
        assertEquals(code, Invites.find("mmmmmmmmmmmm https://blesk.su/i/$code"))
    }

    @Test
    fun `text with no invite has none`() {
        assertNull(Invites.find(""))
        assertNull(Invites.find("Приглашаю тебя в blesk"))
        assertNull(Invites.find(code.dropLast(1)))
        assertNull(Invites.find("https://blesk.su/@anya?k=abc"))
    }
}
