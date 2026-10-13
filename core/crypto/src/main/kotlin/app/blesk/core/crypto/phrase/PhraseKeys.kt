package app.blesk.core.crypto.phrase

import com.google.crypto.tink.subtle.Hkdf
import java.security.MessageDigest

/**
 * Keys that come from the phrase and only from it, each for its own purpose, through HKDF-SHA256.
 * Whoever has the twelve words can rebuild them on a new phone; nothing else can.
 */
class PhraseKeys(entropy: ByteArray) {
    /** Encrypts the account data kept on the server: profile key, contacts, groups, privacy. */
    val account: ByteArray = derive(entropy, "blesk account data v1")

    /** Encrypts the backup file. */
    val backup: ByteArray = derive(entropy, "blesk backup v1")

    /** Proves to the server that the phrase is known, without the phrase or this token ever leaving the phone. */
    val confirmation: ByteArray = derive(entropy, "blesk confirmation v1")

    /** What the server keeps instead of [confirmation]: its SHA-256, which cannot be turned back. */
    val confirmationHash: ByteArray get() = MessageDigest.getInstance("SHA-256").digest(confirmation)

    private companion object {
        const val KEY_BYTES = 32
        val SALT = "blesk recovery phrase".toByteArray()

        fun derive(entropy: ByteArray, purpose: String): ByteArray =
            Hkdf.computeHkdf("HMACSHA256", entropy, SALT, purpose.toByteArray(), KEY_BYTES)
    }
}
