package app.blesk.core.crypto

import app.blesk.core.crypto.phrase.PhraseKeys
import app.blesk.core.crypto.phrase.RecoveryPhrase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class RecoveryPhraseTest {
    @Test
    fun fourLettersAlwaysNameOneWord() {
        val words = RecoveryPhrase.wordList
        assertEquals(2048, words.map { it.take(4) }.toSet().size)
        assertTrue(words.all { it.length in 3..8 && it.all { c -> c in 'а'..'я' } })
    }

    @Test
    fun aPhraseComesBackToTheSameKeys() {
        val words = RecoveryPhrase.generate(SecureRandom())
        assertEquals(RecoveryPhrase.WORDS, words.size)
        val entropy = RecoveryPhrase.entropyOf(words)
        assertNotNull(entropy)
        assertEquals(words, RecoveryPhrase.wordsOf(entropy!!))
        assertArrayEquals(PhraseKeys(entropy).account, PhraseKeys(entropy.copyOf()).account)
    }

    @Test
    fun typingDoesNotHaveToBeExact() {
        val words = RecoveryPhrase.wordsOf(ByteArray(16) { it.toByte() })
        assertNotNull(RecoveryPhrase.entropyOf(words.map { "  ${it.uppercase()} " }))
        assertNotNull(RecoveryPhrase.entropyOf(words.map { it.take(4) }))
    }

    @Test
    fun aSwappedWordFailsTheChecksum() {
        val words = RecoveryPhrase.wordsOf(ByteArray(16) { (it * 7).toByte() }).toMutableList()
        val swapped = (0 until 2048).map { RecoveryPhrase.wordList[it] }.filter { it != words[11] }
        // Several replacements for the last word: at most one in sixteen can pass a 4-bit checksum by chance.
        val passing = swapped.take(64).count { RecoveryPhrase.entropyOf(words.dropLast(1) + it) != null }
        assertTrue(passing < 64)
        assertNull(RecoveryPhrase.entropyOf(words.dropLast(1) + "компьютер"))
        assertNull(RecoveryPhrase.entropyOf(words.dropLast(1)))
    }

    @Test
    fun keysForDifferentPurposesDiffer() {
        val keys = PhraseKeys(ByteArray(16) { 1 })
        assertFalse(keys.account.contentEquals(keys.backup))
        assertFalse(keys.backup.contentEquals(keys.confirmation))
        assertEquals(32, keys.confirmationHash.size)
    }

    @Test
    fun suggestionsFollowThePrefix() {
        assertTrue(RecoveryPhrase.suggestions("ябл").all { it.startsWith("ябл") })
        assertTrue(RecoveryPhrase.suggestions("").isEmpty())
    }
}
