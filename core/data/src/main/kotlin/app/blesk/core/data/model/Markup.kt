package app.blesk.core.data.model

/**
 * Text formatting carried inside the message itself, so it needs nothing from the protocol and
 * reads fine in any client: **bold**, __italic__, ~~strike~~, `mono`, ||spoiler|| and lines that
 * start with "> " as quotes. A marker counts only when it closes on the same line around text
 * that does not start or end with a space, so "2 ** 3" and "~~" stay as typed.
 */
data class Markup(val text: String, val spans: List<Span>) {

    data class Span(val start: Int, val end: Int, val style: Style)

    enum class Style { Bold, Italic, Strike, Mono, Spoiler, Quote }

    /** The text with spoilers replaced by [mask]: for previews, notifications and TalkBack. */
    fun masked(mask: String): String {
        val hidden = spans.filter { it.style == Style.Spoiler }.sortedBy { it.start }
        if (hidden.isEmpty()) return text
        val out = StringBuilder()
        var from = 0
        hidden.forEach { span ->
            if (span.start < from) return@forEach
            out.append(text, from, span.start).append(mask)
            from = span.end
        }
        return out.append(text, from, text.length).toString()
    }

    companion object {
        fun parse(source: String): Markup {
            val out = StringBuilder()
            val spans = mutableListOf<Span>()
            source.split('\n').forEachIndexed { index, line ->
                if (index > 0) out.append('\n')
                val quoted = line.startsWith(QUOTE)
                val start = out.length
                inline(if (quoted) line.removePrefix(QUOTE) else line, out, spans)
                if (quoted) spans += Span(start, out.length, Style.Quote)
            }
            return Markup(out.toString(), spans)
        }

        private fun inline(line: String, out: StringBuilder, spans: MutableList<Span>) {
            var i = 0
            while (i < line.length) {
                val marker = MARKERS.entries.firstOrNull { line.startsWith(it.key, i) }
                val close = marker?.let { closing(line, i, it.key) } ?: -1
                if (marker == null || close < 0) {
                    out.append(line[i])
                    i++
                    continue
                }
                val inner = line.substring(i + marker.key.length, close)
                val start = out.length
                if (marker.value == Style.Mono) out.append(inner) else inline(inner, out, spans)
                spans += Span(start, out.length, marker.value)
                i = close + marker.key.length
            }
        }

        private fun closing(line: String, at: Int, marker: String): Int {
            val from = at + marker.length
            val close = line.indexOf(marker, from)
            if (close <= from) return -1
            val inner = line.substring(from, close)
            return if (inner.first().isWhitespace() || inner.last().isWhitespace()) -1 else close
        }

        private const val QUOTE = "> "

        private val MARKERS = linkedMapOf(
            "**" to Style.Bold,
            "__" to Style.Italic,
            "~~" to Style.Strike,
            "||" to Style.Spoiler,
            "`" to Style.Mono,
        )
    }
}
