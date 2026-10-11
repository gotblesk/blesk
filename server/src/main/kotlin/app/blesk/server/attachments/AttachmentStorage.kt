package app.blesk.server.attachments

import java.io.InputStream

class StoredObject(val size: Long, val content: InputStream)

/** Where encrypted attachment blobs live. Implementations block, so callers run them on the IO dispatcher. */
interface AttachmentStorage {
    /** Streams [content] into object [id] and returns its size. A failed upload leaves nothing behind. */
    fun put(id: String, content: InputStream): Long

    /** Null when the object does not exist. The caller closes [StoredObject.content]. */
    fun open(id: String): StoredObject?

    fun delete(id: String)
}

class PayloadTooLargeException : RuntimeException("payload too large")

/** Fails the read as soon as more than [limit] bytes have come through, before they are stored. */
class SizeLimitedInputStream(private val source: InputStream, private val limit: Long) : InputStream() {
    private var count = 0L

    override fun read(): Int {
        val value = source.read()
        if (value >= 0) advance(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = source.read(buffer, offset, length)
        if (read > 0) advance(read)
        return read
    }

    override fun close() = source.close()

    private fun advance(bytes: Int) {
        count += bytes
        if (count > limit) throw PayloadTooLargeException()
    }
}
