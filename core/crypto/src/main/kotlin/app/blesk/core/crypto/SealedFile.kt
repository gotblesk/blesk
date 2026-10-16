package app.blesk.core.crypto

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * A small file kept sealed with a key from Android Keystore: readable on this phone only, and
 * nothing in it is visible to anyone looking at the app's storage. Writes go through a temporary
 * file, so a crash mid-write never leaves half a file.
 */
class SealedFile(private val file: File, alias: String) {
    private val box = KeystoreBox(alias)

    /** The contents, or null when there is no file yet or it cannot be opened. */
    fun read(): ByteArray? {
        if (!file.exists()) return null
        return try {
            box.open(file.readBytes())
        } catch (_: IOException) {
            null
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    fun write(bytes: ByteArray) {
        val temporary = File(file.parentFile, file.name + ".new")
        temporary.writeBytes(box.seal(bytes))
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("could not replace ${file.name}")
        }
    }
}
