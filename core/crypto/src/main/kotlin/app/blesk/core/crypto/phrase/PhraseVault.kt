package app.blesk.core.crypto.phrase

import android.content.Context
import app.blesk.core.crypto.KeystoreBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val state = MutableStateFlow(read())

    /** Whether there is a phrase, whether it was checked and when it was made; follows every change. */
    val status: StateFlow<PhraseStatus> = state.asStateFlow()

    private fun read() = PhraseStatus(sealed.exists(), checked.exists(), if (sealed.exists()) sealed.lastModified() else 0L)

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
        state.value = read()
    }

    val isChecked: Boolean get() = checked.exists()

    fun markChecked() {
        checked.createNewFile()
        state.value = read()
    }

    fun clear() {
        sealed.delete()
        checked.delete()
        state.value = read()
    }
}

data class PhraseStatus(val exists: Boolean, val checked: Boolean, val createdAtMillis: Long) {
    /** Made and not yet written down: «Ты» reminds about it. */
    val unchecked: Boolean get() = exists && !checked

    /** A day after «Позже» the chats remind about it too. */
    fun overdue(now: Long): Boolean = unchecked && now - createdAtMillis > DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
