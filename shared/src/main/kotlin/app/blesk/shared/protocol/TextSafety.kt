package app.blesk.shared.protocol

import java.text.BreakIterator
import java.text.Normalizer

/**
 * Names, titles, descriptions and «о себе» are cleaned the same way on the phone and on the
 * server: brought to one Unicode form, and stripped of what can break the screen around them —
 * control characters, characters that turn the direction of text, zero-width and blank «letters»,
 * and piles of combining marks («залго»). What is left must show at least one real character.
 */
object TextSafety {
    /** Combining marks kept on one letter; more only stack up over the lines around. */
    const val MAX_MARKS = 2

    fun clean(raw: String, multiline: Boolean = false): String {
        val text = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val out = StringBuilder(text.length)
        var marks = 0
        var i = 0
        while (i < text.length) {
            val code = text.codePointAt(i)
            i += Character.charCount(code)
            when {
                code == NEWLINE && multiline -> {
                    out.appendCodePoint(code)
                    marks = 0
                }
                code == NEWLINE -> out.append(' ')
                code in Removed || isControl(code) -> Unit
                isMark(code) -> if (marks < MAX_MARKS) {
                    out.appendCodePoint(code)
                    marks++
                }
                else -> {
                    out.appendCodePoint(code)
                    marks = 0
                }
            }
        }
        return out.toString().trim()
    }

    /** Whether there is a letter, a digit or an emoji to see. */
    fun hasVisible(text: String): Boolean = text.codePoints().anyMatch { code ->
        Character.isLetterOrDigit(code) || Character.getType(code) == Character.OTHER_SYMBOL.toInt()
    }

    /** Length as the eye counts it: «é» made of two code points is one. */
    fun visibleLength(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    private fun isControl(code: Int) = Character.getType(code) == Character.CONTROL.toInt()

    internal fun isMark(code: Int): Boolean = when (Character.getType(code)) {
        Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
        else -> false
    }

    private const val NEWLINE = '\n'.code

    /** Direction overrides and isolates, zero-width characters, and letters that draw nothing. */
    internal val Removed: Set<Int> = buildSet {
        addAll(0x202A..0x202E)
        addAll(0x2066..0x2069)
        addAll(listOf(0x200E, 0x200F, 0x061C))
        addAll(0x200B..0x200D)
        addAll(listOf(0x2060, 0xFEFF, 0x180E))
        addAll(listOf(0x3164, 0x115F, 0x1160, 0x2800, 0xFFA0, 0x2028, 0x2029))
    }
}
