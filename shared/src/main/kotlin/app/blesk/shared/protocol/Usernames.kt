package app.blesk.shared.protocol

import java.text.Normalizer
import kotlin.random.Random

/**
 * A @username is lower-case latin letters, digits, dot and underscore, 3 to 32 of them, starting
 * with a letter; a dot or underscore never at either end and never two in a row. Latin only, so a
 * look-alike Cyrillic letter cannot pass for someone else. Lower case only, so «Egor» and «egor»
 * are one name.
 *
 * Two levels: [isWellFormed] is the shape every name has, and is all that signing in, links,
 * lookups and messages ask of a name, so a name taken before a rule was added keeps working.
 * [problem] and [isValid] add the rules for a new name: names of the service and their look-alikes
 * are kept back, and names ending like a bot's belong to bots.
 */
object Usernames {
    const val MIN = 3
    const val MAX = 32

    /** How many free names [variants] offers unless asked for another number. */
    const val VARIANTS = 8

    private val pattern = Regex("^[a-z][a-z0-9]*(?:[._][a-z0-9]+)*$")

    fun isWellFormed(name: String): Boolean = name.length in MIN..MAX && pattern.matches(name)

    /** The first rule a new name breaks; null if a new account may take it. */
    fun problem(name: String): UsernameProblem? = when {
        !name.all(::isAllowed) -> UsernameProblem.Characters
        name.length < MIN -> UsernameProblem.TooShort
        name.length > MAX -> UsernameProblem.TooLong
        isSeparator(name.first()) || isSeparator(name.last()) -> UsernameProblem.EdgeSeparator
        name.first() in Digits -> UsernameProblem.StartsWithNonLetter
        name.zipWithNext().any { (a, b) -> isSeparator(a) && isSeparator(b) } -> UsernameProblem.DoubleSeparator
        name.endsWith(Bots.SUFFIX) -> UsernameProblem.BotName
        isReserved(name) -> UsernameProblem.Reserved
        else -> null
    }

    /** Whether a new account may take this name. */
    fun isValid(name: String): Boolean = problem(name) == null

    /**
     * Whether the name is the service's or looks like it. Dots and underscores do not count, and
     * digits pass for the letters they resemble, at the end too: «r00t», «a11» and «suppor7» are kept
     * back. Names someone would pick to pass for the service are read the most loosely, with i and l
     * as one letter and rn as m: «blesk» within a part or from the start of one («b1esk», «my.blesk»,
     * «bl_esk»), and names starting with admin, moderator, support, official or system («adrnin»). The
     * other service words are kept back whole or with digits added («help2024»), and there i stays i,
     * since «ali» and «mali» are people's names, not «all» and «mail».
     */
    fun isReserved(name: String): Boolean {
        val bare = name.filterNot(::isSeparator).replace("rn", "m")
        return spellsService(name) ||
            ReservedPrefixes.any(fold(bare)::startsWith) ||
            listOf(bare, bare.trimEnd { it in Digits }).any { text -> ReservedWords.any { spells(text, it) } }
    }

    /**
     * What is typed or pasted, as a name can be: a profile link gives its name; wide, bold and
     * accented latin letters become plain ones; capitals become small; marks, direction and
     * invisible characters, «@» and spaces go away. Letters of other alphabets go too, and
     * [TypedUsername.droppedNonLatin] says so, because a person may not see why they vanished.
     */
    fun typed(raw: CharSequence): TypedUsername {
        val text = raw.toString()
        val name = BleskLinks.site(text, "/@")?.rest ?: BleskLinks.legacy(text, "blesk://u/")?.rest ?: text
        // NFKD rather than NFKC: composing would merge a letter with its marks into one character the filter drops whole.
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFKD)
        val kept = StringBuilder()
        var droppedNonLatin = false
        var i = 0
        while (i < decomposed.length) {
            val code = decomposed.codePointAt(i)
            i += Character.charCount(code)
            if (TextSafety.isMark(code)) continue
            val lower = Character.toLowerCase(code)
            when {
                lower < ASCII_END && isAllowed(lower.toChar()) -> kept.append(lower.toChar())
                Character.isLetter(code) && code !in TextSafety.Removed -> droppedNonLatin = true
            }
        }
        return TypedUsername(kept.take(MAX).toString(), droppedNonLatin, cut = kept.length > MAX)
    }

    /** What is typed, as a name can be; see [typed]. */
    fun normalize(raw: String): String = typed(raw).text

    /**
     * Up to [count] names like [base] that a new account may take: the name without digits at the
     * end, its parts joined and swapped, then the name with a few digits. Whether they are free is
     * for the server to say. None for a name of the service, since every variant is one too.
     */
    fun variants(base: String, random: Random, count: Int = VARIANTS): List<String> {
        val name = normalize(base)
        val stem = name.removeSuffix(Bots.SUFFIX)
            .trimStart { it !in 'a'..'z' }
            .trimEnd { it in Digits || isSeparator(it) }
            // Room for the longest ending added below: three digits, or a dot or underscore and two.
            .take(MAX - SUFFIX_ROOM)
            .trimEnd(::isSeparator)
        if (stem.isEmpty()) return emptyList()
        val parts = stem.split('.', '_')
        val candidates = buildList {
            add(stem)
            if (parts.size >= 2) {
                add(parts.joinToString(""))
                add(parts.reversed().joinToString(stem.first(::isSeparator).toString()))
            }
            repeat(count * 2) { shape ->
                add(
                    when (shape % 3) {
                        0 -> "$stem${random.nextInt(1, 1000)}"
                        1 -> "$stem.${random.nextInt(10, 100)}"
                        else -> "${stem}_${random.nextInt(10, 100)}"
                    },
                )
            }
        }
        return candidates.distinct().filter { it != name && it != base && isValid(it) }.take(count)
    }

    private const val SUFFIX_ROOM = 3
    private const val ASCII_END = 0x80
    private val Digits = '0'..'9'

    private fun isSeparator(char: Char) = char == '.' || char == '_'

    private fun isAllowed(char: Char) = char in 'a'..'z' || char in Digits || isSeparator(char)

    /** The letters each digit can be read as. */
    private val DigitLetters =
        mapOf('0' to "o", '1' to "li", '3' to "e", '4' to "a", '5' to "s", '6' to "b", '7' to "t")

    /** Each digit as its first letter and i as l, so names that read alike come out the same. One letter in, one out. */
    private fun fold(text: String): String = buildString(text.length) {
        text.forEach { char -> append(if (char == 'i') 'l' else DigitLetters[char]?.first() ?: char) }
    }

    /** Whether [text] is [word] with digits written for some of its letters. */
    private fun spells(text: String, word: String): Boolean =
        text.length == word.length && word.indices.all { text[it] == word[it] || word[it] in DigitLetters[text[it]].orEmpty() }

    /**
     * «blesk» inside one part of the name, or read on from the start of a part: «b.l.e.s.k» and
     * «my_blesk» are the service, while «gleb.leskov» and «noble.sky» only meet it across a dot.
     */
    private fun spellsService(name: String): Boolean {
        val bare = name.filterNot(::isSeparator)
        // The part of the name each letter of [bare] comes from.
        val part = IntArray(bare.length)
        var index = 0
        var at = 0
        name.forEach { char -> if (isSeparator(char)) index++ else part[at++] = index }
        return Service.findAll(fold(bare)).any { match ->
            val start = match.range.first
            start == 0 || part[start - 1] != part[start] || part[start] == part[match.range.last]
        }
    }

    private val Service = Regex.fromLiteral("blesk")

    private val ReservedWords: List<String> = listOf(
        "admin", "administrator", "support", "blesk", "system", "moderator", "mod", "root", "help",
        "official", "security", "staff", "service", "owner", "team", "info", "abuse", "noreply",
        "admins", "moderators", "sysadmin", "superuser", "postmaster", "hostmaster", "webmaster",
        "anonymous", "everyone", "all", "here", "bot", "bots", "api", "www", "mail", "news", "updates",
        "privacy", "terms", "legal", "contact", "press", "status", "settings", "verify", "verified",
        "notifications", "spam", "report", "safety",
    )

    private val ReservedPrefixes: List<String> = listOf("admin", "moderator", "support", "official", "system").map(::fold)
}

/** The first rule a new name breaks; see [Usernames.problem]. */
enum class UsernameProblem {
    /** Something other than small latin letters, digits, a dot or an underscore. */
    Characters,
    TooShort,
    TooLong,
    StartsWithNonLetter,

    /** A dot or an underscore at the start or the end. */
    EdgeSeparator,

    /** Two dots or underscores in a row. */
    DoubleSeparator,

    /** Ends like a bot's name. */
    BotName,

    /** The service's own name or one like it. */
    Reserved,
}

/**
 * A name as typed: [text] is what is left of it. [droppedNonLatin] is set when letters of another
 * alphabet were taken out, and [cut] when it was longer than [Usernames.MAX].
 */
data class TypedUsername(val text: String, val droppedNonLatin: Boolean, val cut: Boolean)
