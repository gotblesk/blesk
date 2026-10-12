package app.blesk.core.crypto

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.util.SecretBytes
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.security.SecureRandom

/**
 * Attachments encrypted in 1 MB segments with Tink's AES-GCM-HKDF streaming AEAD: each segment
 * is authenticated on its own, so a video can be decrypted and played from any point while it
 * is still downloading, and a file never has to fit in memory. The 32-byte key is fresh per file
 * and travels only inside the Signal message.
 */
object StreamingCipher {
    const val KEY_BYTES = 32
    private val context = "blesk-attachment".encodeToByteArray()

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        aead(key).newEncryptingStream(output, context).use { input.copyTo(it, BUFFER) }
    }

    /** Plaintext of a ciphertext read from start to end; a tampered segment fails as it is reached. */
    fun decrypting(key: ByteArray, ciphertext: InputStream): InputStream = aead(key).newDecryptingStream(ciphertext, context)

    /** Plaintext with random access over a ciphertext that can itself be read at any position. */
    fun seekable(key: ByteArray, ciphertext: SeekableByteChannel): SeekableByteChannel =
        aead(key).newSeekableDecryptingChannel(ciphertext, context)

    /** How long the ciphertext of [plaintextBytes] bytes will be, for the upload to announce its size. */
    fun ciphertextSize(key: ByteArray, plaintextBytes: Long): Long =
        (aead(key) as AesGcmHkdfStreaming).expectedCiphertextSize(plaintextBytes)

    private fun aead(key: ByteArray): StreamingAead {
        require(key.size == KEY_BYTES)
        val parameters = AesGcmHkdfStreamingParameters.builder()
            .setKeySizeBytes(KEY_BYTES)
            .setDerivedAesGcmKeySizeBytes(KEY_BYTES)
            .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
            .setCiphertextSegmentSizeBytes(SEGMENT_BYTES)
            .build()
        val secret = SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get())
        return AesGcmHkdfStreaming.create(AesGcmHkdfStreamingKey.create(parameters, secret))
    }

    private const val SEGMENT_BYTES = 1 shl 20
    private const val BUFFER = 64 * 1024
}
