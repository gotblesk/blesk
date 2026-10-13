package app.blesk.core.crypto.phrase

import android.content.Context
import app.blesk.core.crypto.KeystoreBox
import java.io.File
import java.security.GeneralSecurityException

/**
 * The phrase of this phone's account, sealed by Android Keystore, so it can be shown again after
 * the app's code or a fingerprint. Also whether it was checked (three words typed back) and when it
 * was made, for the reminder to write it down.
 */
class PhraseVault(context: Context) {
    private val dir = File(context.noBackupFilesDir, "phrase").apply { mkdirs() }
    private val sealed = File(dir, "entropy")
    private val checked = File(dir, "checked")
    private val box = KeystoreBox("blesk.phrase")

    val exists: Boolean get() = sealed.exists()

    /** The phrase's 128 bits; null when there is none or this phone's Keystore cannot open it. */
    fun entropy(): ByteArray? = try {
        if (sealed.exists()) box.open(sealed.readBytes()) else null
    } catch (_: GeneralSecurityException) {
        null
    }

    fun words(): List<String>? = entropy()?.let(RecoveryPhrase::wordsOf)

    /** Keeps a new phrase; it starts unchecked. */
    fun save(entropy: ByteArray) {
        sealed.writeBytes(box.seal(entropy))
        checked.delete()
    }

    val isChecked: Boolean get() = checked.exists()

    fun markChecked() {
        checked.createNewFile()
    }

    /** When the phrase was made, for the banner a day after «Позже»; 0 without one. */
    val createdAtMillis: Long get() = if (sealed.exists()) sealed.lastModified() else 0L

    fun clear() {
        sealed.delete()
        checked.delete()
    }
}
