package app.blesk.core.crypto

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Attachment encryption as Signal does it: AES-256-CBC with PKCS#7, then HMAC-SHA256 over
 * IV and ciphertext (encrypt-then-MAC). A fresh 64-byte key per file (32 for AES, 32 for the
 * MAC) travels only inside the Signal message. The MAC is checked over the whole file before
 * a single byte is decrypted.
 */
object AttachmentCipher {
    const val KEY_BYTES = 64
    private const val IV_BYTES = 16
    private const val MAC_BYTES = 32
    private const val BUFFER = 64 * 1024

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

    /** Encrypts [input] into [output] as IV || ciphertext || MAC. */
    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        require(key.size == KEY_BYTES)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, 0, 32, "AES"), IvParameterSpec(iv))
        }
        val mac = mac(key)
        val macing = MacOutputStream(output, mac)
        macing.write(iv)
        CipherOutputStream(NonClosing(macing), cipher).use { input.copyTo(it, BUFFER) }
        output.write(mac.doFinal())
        output.flush()
    }

    /** Verifies and decrypts [encrypted] into [output]. Throws [IOException] if it was tampered with. */
    fun decrypt(key: ByteArray, encrypted: File, output: OutputStream) {
        require(key.size == KEY_BYTES)
        val length = encrypted.length()
        if (length < IV_BYTES + MAC_BYTES + 16) throw IOException("Attachment too short")
        verify(key, encrypted, length)
        encrypted.inputStream().use { stream ->
            val iv = stream.readExactly(IV_BYTES)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, 0, 32, "AES"), IvParameterSpec(iv))
            }
            stream.forEachChunk(length - IV_BYTES - MAC_BYTES) { buffer, read -> cipher.update(buffer, 0, read)?.let(output::write) }
            output.write(cipher.doFinal())
        }
        output.flush()
    }

    /** Checks the MAC over the whole file before any decryption happens. */
    private fun verify(key: ByteArray, encrypted: File, length: Long) {
        val mac = mac(key)
        val expected = encrypted.inputStream().use { stream ->
            stream.forEachChunk(length - MAC_BYTES) { buffer, read -> mac.update(buffer, 0, read) }
            stream.readExactly(MAC_BYTES)
        }
        if (!MessageDigest.isEqual(mac.doFinal(), expected)) throw IOException("Attachment MAC mismatch")
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            if (read < 0) throw IOException("Unexpected end")
            offset += read
        }
        return bytes
    }

    private inline fun InputStream.forEachChunk(total: Long, block: (ByteArray, Int) -> Unit) {
        var remaining = total
        val buffer = ByteArray(BUFFER)
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Unexpected end")
            block(buffer, read)
            remaining -= read
        }
    }

    private fun mac(key: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, 32, 32, "HmacSHA256")) }

    private class MacOutputStream(private val target: OutputStream, private val mac: Mac) : OutputStream() {
        override fun write(b: Int) {
            mac.update(b.toByte())
            target.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            mac.update(b, off, len)
            target.write(b, off, len)
        }
    }

    /** Lets the cipher stream finish its last block without closing the caller's stream. */
    private class NonClosing(private val target: OutputStream) : OutputStream() {
        override fun write(b: Int) = target.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
        override fun flush() = target.flush()
        override fun close() = target.flush()
    }
}
