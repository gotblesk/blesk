package app.blesk.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.random.Random

class StreamingCipherTest {
    private val key = StreamingCipher.newKey()
    private val plain = Random.nextBytes(3 * 1024 * 1024 + 77)
    private val sealed = ByteArrayOutputStream().also { StreamingCipher.encrypt(key, plain.inputStream(), it) }.toByteArray()

    @Test
    fun `round trip and the size is known in advance`() {
        assertEquals(sealed.size.toLong(), StreamingCipher.ciphertextSize(key, plain.size.toLong()))
        assertArrayEquals(plain, StreamingCipher.decrypting(key, sealed.inputStream()).readBytes())
    }

    @Test
    fun `any position can be read without the start`() {
        val channel = StreamingCipher.seekable(key, BytesChannel(sealed))
        channel.position(2_500_000)
        val buffer = ByteBuffer.allocate(1000)
        while (buffer.hasRemaining()) channel.read(buffer)
        assertArrayEquals(plain.copyOfRange(2_500_000, 2_501_000), buffer.array())
    }

    @Test
    fun `a flipped byte is refused`() {
        val broken = sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertThrows(IOException::class.java) { StreamingCipher.decrypting(key, broken.inputStream()).readBytes() }
    }

    /** A ciphertext in memory with random access, as the downloaded part of a file would be. */
    private class BytesChannel(private val bytes: ByteArray) : SeekableByteChannel {
        private var position = 0L

        override fun read(destination: ByteBuffer): Int {
            if (position >= bytes.size) return -1
            val count = minOf(destination.remaining().toLong(), bytes.size - position).toInt()
            destination.put(bytes, position.toInt(), count)
            position += count
            return count
        }

        override fun write(source: ByteBuffer) = throw UnsupportedOperationException()

        override fun position() = position

        override fun position(newPosition: Long) = apply { position = newPosition }

        override fun size() = bytes.size.toLong()

        override fun truncate(size: Long) = throw UnsupportedOperationException()

        override fun isOpen() = true

        override fun close() = Unit
    }
}
