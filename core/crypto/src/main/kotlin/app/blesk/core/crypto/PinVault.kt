package app.blesk.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Checks the app lock code without ever storing it. The code (4 or 6 digits, or a password) is
 * stretched with Argon2id and then MACed with HMAC-SHA256 under a key that lives in Android
 * Keystore and cannot be exported: a copied file is useless off the device, and every guess on
 * it costs a slow hash. Wrong attempts are throttled with a delay that survives restarts.
 */
class PinVault(context: Context, name: String = "pin", private val clock: () -> Long = System::currentTimeMillis) {
    private val file = File(context.noBackupFilesDir, name)
    private val keys: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    val isSet: Boolean get() = file.exists()

    /** Digits of the stored code (4 or 6); 0 for a password or when nothing is set. */
    val length: Int get() = read()?.length ?: 0

    /** Whether the code is a password typed on the keyboard rather than digits. */
    val isPassword: Boolean get() = read()?.let { it.length == PASSWORD } ?: false

    /** Wrong attempts in a row so far. */
    val failures: Int get() = read()?.failures ?: 0

    suspend fun set(code: String) = withContext(Dispatchers.Default) {
        val digits = code.all(Char::isDigit) && (code.length == 4 || code.length == 6)
        require(digits || code.length >= MIN_PASSWORD) { "a code is 4 or 6 digits or a password of $MIN_PASSWORD or more" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        write(Record(if (digits) code.length else PASSWORD, salt, verifier(salt, code), failures = 0, lockedUntil = 0))
    }

    fun clear() {
        val existed = file.delete()
        if (existed && keys.containsAlias(ALIAS)) keys.deleteEntry(ALIAS)
    }

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val waitMillis: Long, val failures: Int) : Result
        data class Throttled(val waitMillis: Long) : Result
    }

    suspend fun check(code: String): Result = withContext(Dispatchers.Default) {
        val record = read() ?: return@withContext Result.Ok
        val now = clock()
        if (record.lockedUntil > now) return@withContext Result.Throttled(record.lockedUntil - now)
        val expected = if (record.legacy) mac(record.salt, code.toByteArray()) else verifier(record.salt, code)
        if (MessageDigest.isEqual(expected, record.verifier)) {
            // A code set before the slow hash existed is moved to it on the first right entry.
            if (record.legacy) {
                write(record.copy(verifier = verifier(record.salt, code), failures = 0, lockedUntil = 0, legacy = false))
            } else if (record.failures > 0) {
                write(record.copy(failures = 0, lockedUntil = 0))
            }
            return@withContext Result.Ok
        }
        val failures = record.failures + 1
        val wait = backoff(failures)
        write(record.copy(failures = failures, lockedUntil = now + wait))
        Result.Wrong(wait, failures)
    }

    private fun verifier(salt: ByteArray, code: String): ByteArray = mac(salt, stretch(salt, code))

    private fun stretch(salt: ByteArray, code: String): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(ARGON_ITERATIONS)
            .withMemoryAsKB(ARGON_MEMORY_KB)
            .withParallelism(1)
            .build()
        return ByteArray(STRETCHED_BYTES).also { out ->
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(code.toByteArray(), out)
        }
    }

    private fun mac(salt: ByteArray, input: ByteArray): ByteArray =
        Mac.getInstance(HMAC).run {
            init(key())
            update(salt)
            doFinal(input)
        }

    private fun key(): SecretKey {
        (keys.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    /** [legacy] codes were MACed directly, before Argon2id; they are upgraded on the next right entry. */
    private data class Record(
        val length: Int,
        val salt: ByteArray,
        val verifier: ByteArray,
        val failures: Int,
        val lockedUntil: Long,
        val legacy: Boolean = false,
    )

    private fun read(): Record? {
        if (!file.exists()) return null
        val parts = file.readText().split(':')
        return when (parts.size) {
            LEGACY_FIELDS -> parts.toRecord(from = 0, legacy = true)
            FIELDS -> parts.toRecord(from = 1, legacy = false)
            else -> null
        }
    }

    private fun List<String>.toRecord(from: Int, legacy: Boolean) = Record(
        length = this[from].toInt(),
        salt = this[from + 1].hexToBytes(),
        verifier = this[from + 2].hexToBytes(),
        failures = this[from + 3].toInt(),
        lockedUntil = this[from + 4].toLong(),
        legacy = legacy,
    )

    private fun write(record: Record) {
        file.parentFile?.mkdirs()
        val fields =
            listOf(record.length, record.salt.toHex(), record.verifier.toHex(), record.failures, record.lockedUntil)
        file.writeText((if (record.legacy) fields else listOf(VERSION) + fields).joinToString(":"))
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "blesk.pin"
        private const val HMAC = "HmacSHA256"
        private const val SALT_BYTES = 16
        private const val STRETCHED_BYTES = 32
        private const val VERSION = 2
        private const val FIELDS = 6
        private const val LEGACY_FIELDS = 5
        private const val FREE_ATTEMPTS = 4
        private const val PASSWORD = 0
        const val MIN_PASSWORD = 6

        // About a quarter of a second on a mid-range phone: unnoticeable once, ruinous for guessing.
        private const val ARGON_ITERATIONS = 3
        private const val ARGON_MEMORY_KB = 32 * 1024

        /** After four free mistakes: 30 seconds, a minute, five minutes, then a quarter of an hour each time. */
        private val DELAYS = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L)

        fun backoff(failures: Int): Long {
            if (failures <= FREE_ATTEMPTS) return 0
            return DELAYS[(failures - FREE_ATTEMPTS - 1).coerceAtMost(DELAYS.lastIndex)]
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
