package app.blesk.core.data

import app.blesk.core.data.model.Markup
import app.blesk.core.data.model.Markup.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkupTest {

    @Test
    fun stripsMarkersAndKeepsRanges() {
        val markup = Markup.parse("a **b** __c__ ~~d~~ `e` ||f||")
        assertEquals("a b c d e f", markup.text)
        assertEquals(
            listOf(Style.Bold, Style.Italic, Style.Strike, Style.Mono, Style.Spoiler),
            markup.spans.map { it.style },
        )
        markup.spans.forEach { assertEquals(1, it.end - it.start) }
    }

    @Test
    fun nestsInsideButNotInMono() {
        val markup = Markup.parse("**bold __both__** `**raw**`")
        assertEquals("bold both **raw**", markup.text)
        assertEquals(setOf(Style.Bold, Style.Italic, Style.Mono), markup.spans.map { it.style }.toSet())
    }

    @Test
    fun leavesLooseMarkersAlone() {
        listOf("2 ** 3", "~~", "** spaced **", "a`b", "line **one\ntwo**").forEach {
            assertEquals(it, Markup.parse(it).text)
        }
    }

    @Test
    fun quotesWholeLines() {
        val markup = Markup.parse("> cited\nanswer")
        assertEquals("cited\nanswer", markup.text)
        assertEquals(Markup.Span(0, 5, Style.Quote), markup.spans.single())
    }

    @Test
    fun masksSpoilers() {
        assertEquals("ends with …!", Markup.parse("ends with ||secret||!").masked("…"))
    }
}
