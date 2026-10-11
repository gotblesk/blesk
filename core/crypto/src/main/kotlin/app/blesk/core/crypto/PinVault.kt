package app.blesk.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Checks the app lock PIN without ever storing it. The verifier is HMAC-SHA256 under a key
 * that lives in Android Keystore and cannot be exported, so the stored file is useless
 * off the device: a 4-digit PIN cannot be brute-forced from a copied file.
 * Wrong attempts are throttled with a growing delay that survives restarts.
 */
class PinVault(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val file = File(context.noBackupFilesDir, "pin")
    private val keys: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    val isSet: Boolean get() = file.exists()

    /** Length of the stored PIN (4 or 6), or 0 when no PIN is set. */
    val length: Int get() = read()?.length ?: 0

    fun set(pin: String) {
        require((pin.length == 4 || pin.length == 6) && pin.all(Char::isDigit)) { "PIN must be 4 or 6 digits" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        write(Record(pin.length, salt, mac(salt, pin), failures = 0, lockedUntil = 0))
    }

    fun clear() {
        val existed = file.delete()
        if (existed && keys.containsAlias(ALIAS)) keys.deleteEntry(ALIAS)
    }

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val waitMillis: Long) : Result
        data class Throttled(val waitMillis: Long) : Result
    }

    fun check(pin: String): Result {
        val record = read() ?: return Result.Ok
        val now = clock()
        if (record.lockedUntil > now) return Result.Throttled(record.lockedUntil - now)
        if (MessageDigest.isEqual(mac(record.salt, pin), record.verifier)) {
            if (record.failures > 0) write(record.copy(failures = 0, lockedUntil = 0))
            return Result.Ok
        }
        val failures = record.failures + 1
        val wait = backoff(failures)
        write(record.copy(failures = failures, lockedUntil = now + wait))
        return Result.Wrong(wait)
    }

    private fun mac(salt: ByteArray, pin: String): ByteArray =
        Mac.getInstance(HMAC).run {
            init(key())
            update(salt)
            doFinal(pin.toByteArray())
        }

    private fun key(): SecretKey {
        (keys.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private data class Record(val length: Int, val salt: ByteArray, val verifier: ByteArray, val failures: Int, val lockedUntil: Long)

    private fun read(): Record? {
        if (!file.exists()) return null
        val parts = file.readText().split(':')
        if (parts.size != FIELDS) return null
        return Record(
            length = parts[0].toInt(),
            salt = parts[1].hexToBytes(),
            verifier = parts[2].hexToBytes(),
            failures = parts[3].toInt(),
            lockedUntil = parts[4].toLong(),
        )
    }

    private fun write(record: Record) {
        file.parentFile?.mkdirs()
        file.writeText(
            listOf(record.length, record.salt.toHex(), record.verifier.toHex(), record.failures, record.lockedUntil).joinToString(":"),
        )
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "blesk.pin"
        private const val HMAC = "HmacSHA256"
        private const val SALT_BYTES = 16
        private const val FIELDS = 5
        private const val FREE_ATTEMPTS = 4

        /** No delay for the first few mistakes, then 30 s doubling up to an hour. */
        fun backoff(failures: Int): Long {
            if (failures <= FREE_ATTEMPTS) return 0
            val steps = (failures - FREE_ATTEMPTS - 1).coerceAtMost(7)
            return (30_000L shl steps).coerceAtMost(3_600_000L)
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
