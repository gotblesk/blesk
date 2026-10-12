package app.blesk.core.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import app.blesk.core.crypto.StreamingCipher
import app.blesk.core.data.StreamSource
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * Plays a video while its ciphertext is still arriving: reads wait until the bytes they need are
 * on disk, and Tink decrypts each segment as it is reached, from any position.
 */
@OptIn(UnstableApi::class)
class EncryptedStream(private val source: StreamSource) : BaseDataSource(false) {
    private var channel: SeekableByteChannel? = null
    private var uri: Uri? = null
    private var left = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val plain = StreamingCipher.seekable(source.key, GrowingFile(source.ciphertext, source.ciphertextBytes))
        plain.position(dataSpec.position)
        channel = plain
        uri = dataSpec.uri
        left = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else plain.size() - dataSpec.position
        transferStarted(dataSpec)
        return left
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (left <= 0) return C.RESULT_END_OF_INPUT
        val read = checkNotNull(channel).read(ByteBuffer.wrap(buffer, offset, minOf(length.toLong(), left).toInt()))
        if (read < 0) return C.RESULT_END_OF_INPUT
        left -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        channel?.close()
        channel = null
        uri = null
        transferEnded()
    }

    companion object {
        fun factory(source: StreamSource) = DataSource.Factory { EncryptedStream(source) }
    }
}

/**
 * A file being downloaded, read as if it were complete: a read past what has arrived waits for
 * it. The handle stays valid even after the download finishes and removes the file.
 */
private class GrowingFile(private val file: File, private val size: Long) : SeekableByteChannel {
    private var handle: RandomAccessFile? = null
    private var position = 0L

    override fun read(destination: ByteBuffer): Int {
        if (position >= size) return -1
        val wanted = minOf(destination.remaining().toLong(), size - position)
        val raf = await(position + wanted)
        raf.seek(position)
        val read = raf.channel.read(destination)
        if (read > 0) position += read
        return read
    }

    /** Waits until [end] bytes are on disk, or gives up when nothing has arrived for a while. */
    private fun await(end: Long): RandomAccessFile {
        var waited = 0L
        var seen = -1L
        while (true) {
            val raf = handle ?: file.takeIf { it.exists() }?.let { RandomAccessFile(it, "r") }?.also { handle = it }
            val have = raf?.length() ?: 0L
            if (raf != null && have >= end) return raf
            if (have != seen) {
                seen = have
                waited = 0
            }
            if (waited > STALL_MILLIS) throw IOException("download stalled")
            Thread.sleep(POLL_MILLIS)
            waited += POLL_MILLIS
        }
    }

    override fun write(source: ByteBuffer) = throw UnsupportedOperationException()

    override fun position() = position

    override fun position(newPosition: Long) = apply { position = newPosition }

    override fun size() = size

    override fun truncate(size: Long) = throw UnsupportedOperationException()

    override fun isOpen() = true

    override fun close() {
        handle?.close()
        handle = null
    }

    private companion object {
        const val POLL_MILLIS = 50L
        const val STALL_MILLIS = 30_000L
    }
}
