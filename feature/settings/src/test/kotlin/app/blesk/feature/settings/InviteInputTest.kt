package app.blesk.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the «Инвайт-код» field makes of the text put into it. */
class InviteInputTest {
    private val code = "abcdefghjkmn"

    @Test
    fun aCodeOrItsLinkIsTheCode() {
        assertEquals(code, inviteIn(code))
        assertEquals(code, inviteIn(" ABCDEFGHJKMN "))
        assertEquals(code, inviteIn("https://blesk.su/i/$code"))
        assertEquals(code, inviteIn("www.blesk.su/i/$code/"))
    }

    @Test
    fun aWholeCopiedMessageGivesTheCodeOfItsLink() {
        assertEquals(code, inviteIn("Приглашаю тебя в blesk: https://blesk.su/i/$code"))
        assertEquals(code, inviteIn("Приглашаю тебя в blesk:\nhttps://blesk.su/i/$code."))
        assertEquals(code, inviteIn("Вот код: «$code», заходи"))
    }

    @Test
    fun aLinkWinsOverAWordThatOnlyLooksLikeACode() {
        assertEquals(code, inviteIn("mmmmmmmmmmmm https://blesk.su/i/$code"))
    }

    @Test
    fun nothingLikeAnInviteIsNone() {
        assertNull(inviteIn(""))
        assertNull(inviteIn("Приглашаю тебя в blesk"))
        assertNull(inviteIn("abcdefghjkm"))
        assertNull(inviteIn("https://blesk.su/@anya?k=abc"))
    }
}
