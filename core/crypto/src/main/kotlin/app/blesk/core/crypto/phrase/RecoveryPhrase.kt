package app.blesk.core.crypto.phrase

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The twelve words that are the account. 128 random bits and a 4-bit checksum of their SHA-256
 * make 132 bits, cut into twelve 11-bit numbers, each a word of a 2048-word list: the scheme of
 * BIP-39, with Russian nouns of three to seven letters, easy to write down and to type.
 */
object RecoveryPhrase {
    const val WORDS = 12
    private const val ENTROPY_BYTES = 16
    private const val BITS_PER_WORD = 11
    private const val CHECKSUM_BITS = 4
    private const val LIST_SIZE = 2048

    /** The word list, in its fixed order: a word's place in it is its number. */
    val wordList: List<String> by lazy {
        val stream =
            checkNotNull(RecoveryPhrase::class.java.getResourceAsStream("words_ru.txt")) { "Word list missing" }
        stream.bufferedReader().useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
            .also { check(it.size == LIST_SIZE && it.toSet().size == LIST_SIZE) { "Word list must hold $LIST_SIZE distinct words" } }
    }

    private val numberOf: Map<String, Int> by lazy { wordList.withIndex().associate { (index, word) -> word to index } }

    /** A new phrase from the system's secure random source. */
    fun generate(random: SecureRandom = SecureRandom()): List<String> =
        wordsOf(ByteArray(ENTROPY_BYTES).also(random::nextBytes))

    fun wordsOf(entropy: ByteArray): List<String> {
        require(entropy.size == ENTROPY_BYTES)
        val bits = bitsOf(entropy) + bitsOf(checksumOf(entropy)).take(CHECKSUM_BITS)
        return bits.chunked(BITS_PER_WORD).map { chunk -> wordList[chunk.fold(0) { acc, bit -> acc shl 1 or bit }] }
    }

    /** The 128 bits behind [words], or null for an unknown word, a wrong count or a checksum that does not match. */
    fun entropyOf(words: List<String>): ByteArray? {
        if (words.size != WORDS) return null
        val numbers = words.map { numberOf[normalize(it)] ?: return null }
        val bits = numbers.flatMap { number -> (BITS_PER_WORD - 1 downTo 0).map { number shr it and 1 } }
        val entropy = ByteArray(ENTROPY_BYTES) { index ->
            bits.subList(index * Byte.SIZE_BITS, (index + 1) * Byte.SIZE_BITS).fold(0) { acc, bit -> acc shl 1 or bit }.toByte()
        }
        val expected = bitsOf(checksumOf(entropy)).take(CHECKSUM_BITS)
        return if (bits.takeLast(CHECKSUM_BITS) == expected) entropy else null
    }

    fun isWord(word: String): Boolean = normalize(word) in numberOf

    /** Words starting with what was typed, for the suggestions while entering the phrase. */
    fun suggestions(prefix: String, limit: Int = 4): List<String> {
        val start = normalize(prefix)
        if (start.isEmpty()) return emptyList()
        return wordList.asSequence().filter { it.startsWith(start) }.take(limit).toList()
    }

    /** Typed on any keyboard: case, «ё» and stray spaces do not matter. */
    fun normalize(word: String): String = word.trim().lowercase().replace('ё', 'е')

    private fun checksumOf(entropy: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(entropy)

    private fun bitsOf(bytes: ByteArray): List<Int> =
        bytes.flatMap { byte -> (Byte.SIZE_BITS - 1 downTo 0).map { byte.toInt() shr it and 1 } }
}
