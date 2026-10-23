package app.blesk.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSafetyTest {
    @Test
    fun removesDirectionAndZeroWidth() {
        assertEquals("Аня Соколова", TextSafety.clean("‮Аня​ Соколова⁦"))
    }

    @Test
    fun keepsAtMostTwoMarksPerLetter() {
        val zalgo = "q\u0301\u0302\u0303\u0304b"
        assertEquals("q\u0301\u0302b", TextSafety.clean(zalgo))
    }

    @Test
    fun noLineBreaksInNames() {
        assertEquals("Аня Соколова", TextSafety.clean("Аня\nСоколова"))
        assertEquals("одна\nдве", TextSafety.clean("одна\nдве", multiline = true))
    }

    @Test
    fun blankFillersAreNotAName() {
        val blank = TextSafety.clean("ㅤ⠀​")
        assertFalse(TextSafety.hasVisible(blank))
        assertTrue(TextSafety.hasVisible("🙂"))
    }

    @Test
    fun countsWhatTheEyeSees() = assertEquals(1, TextSafety.visibleLength("é"))
}
