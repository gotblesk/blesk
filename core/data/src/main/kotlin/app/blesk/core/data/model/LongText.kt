package app.blesk.core.data.model

/** The longest text one message carries; longer text goes out as several messages in order. */
const val MAX_MESSAGE_CHARS = 4096

/**
 * Cuts [text] into parts of at most [limit] characters, preferring a line break, then a space,
 * in the second half of each part, and never splitting a surrogate pair.
 */
fun splitLongText(text: String, limit: Int = MAX_MESSAGE_CHARS): List<String> {
    val parts = ArrayList<String>()
    var rest = text.trim()
    while (rest.length > limit) {
        val window = rest.substring(0, limit)
        val cut = listOf(window.lastIndexOf('\n'), window.lastIndexOf(' '))
            .firstOrNull { it > limit / 2 }
            ?: if (rest[limit - 1].isHighSurrogate()) limit - 1 else limit
        parts += rest.substring(0, cut).trimEnd()
        rest = rest.substring(cut).trimStart()
    }
    if (rest.isNotEmpty()) parts += rest
    return parts
}
