package app.blesk.core.crypto

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * The SQLCipher passphrase: 32 random bytes, stored only sealed by a key that lives in Android
 * Keystore and never leaves it. Without this phone's Keystore the database file cannot be opened.
 */
class DatabaseKey(context: Context) {
    private val file = File(context.noBackupFilesDir, "db.key")
    private val box = KeystoreBox("blesk.database")

    fun obtain(): ByteArray {
        if (file.exists()) return box.open(file.readBytes())
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        file.writeBytes(box.seal(passphrase))
        return passphrase
    }

    private companion object {
        const val PASSPHRASE_BYTES = 32
    }
}
