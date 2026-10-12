package app.blesk.core.data

import app.blesk.core.data.model.splitLongText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTextTest {

    @Test
    fun shortTextStaysWhole() {
        assertEquals(listOf("привет"), splitLongText("  привет \n", limit = 10))
        assertEquals(emptyList<String>(), splitLongText("   ", limit = 10))
    }

    @Test
    fun cutsAtWordsAndKeepsEverything() {
        val text = (1..40).joinToString(" ") { "слово$it" }
        val parts = splitLongText(text, limit = 50)
        assertTrue(parts.all { it.length <= 50 })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test
    fun prefersLineBreaks() {
        assertEquals(listOf("первая строка", "вторая строка"), splitLongText("первая строка\nвторая строка", limit = 16))
    }

    @Test
    fun neverSplitsSurrogatePairs() {
        val parts = splitLongText("a" + "😀".repeat(10), limit = 4)
        assertEquals("a" + "😀".repeat(10), parts.joinToString(""))
        parts.forEach { part -> assertTrue(!part.first().isLowSurrogate() && !part.last().isHighSurrogate()) }
    }
}
