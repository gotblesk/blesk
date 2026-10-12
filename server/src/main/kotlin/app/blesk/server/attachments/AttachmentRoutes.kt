package app.blesk.server.attachments

import app.blesk.server.Services
import app.blesk.server.auth.Tokens
import app.blesk.server.auth.account
import app.blesk.server.http.badRequest
import app.blesk.server.http.ensure
import app.blesk.server.http.forbidden
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.server.http.tooLarge
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.UploadStart
import app.blesk.shared.protocol.UploadStarted
import app.blesk.shared.protocol.UploadState
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Enumeration

private val AttachmentId = Regex("^[A-Za-z0-9_-]{22}$")

/** The object a chunk of a chunked attachment is stored under. */
fun chunkName(id: String, n: Int) = "$id.$n"

fun Route.attachmentRoutes(services: Services) {
    post(Api.ATTACHMENTS) {
        val declared = call.request.contentLength()
        ensure(declared == null || declared <= Api.MAX_ATTACHMENT_BYTES) { tooLarge() }
        val id = Tokens.newId()
        val body = SizeLimitedInputStream(call.receiveChannel().toInputStream(), Api.MAX_ATTACHMENT_BYTES)
        val size = try {
            withContext(Dispatchers.IO) { body.use { services.storage.put(id, it) } }
        } catch (tooBig: PayloadTooLargeException) {
            throw tooLarge(tooBig)
        }
        services.attachments.record(id, size, services.now())
        call.respond(AttachmentUploaded(id))
    }

    post(Api.UPLOADS) {
        val start = call.receiveJson<UploadStart>()
        ensure(start.size > 0) { badRequest() }
        ensure(start.size <= Api.MAX_CHUNKED_BYTES) { tooLarge() }
        val id = Tokens.newId()
        val chunks = ((start.size + Api.CHUNK_BYTES - 1) / Api.CHUNK_BYTES).toInt()
        services.attachments.startChunked(id, call.account.id, start.size, chunks, services.now())
        call.respond(UploadStarted(id, Api.CHUNK_BYTES))
    }

    put("${Api.ATTACHMENTS}/{id}/chunks/{n}") {
        val (id, upload) = call.ownUpload(services)
        val n = call.parameters["n"]?.toIntOrNull()?.takeIf { it in 0 until upload.chunks }.orFail { badRequest() }
        ensure(!upload.complete) { badRequest() }
        val expected = if (n == upload.chunks - 1) upload.size - n.toLong() * Api.CHUNK_BYTES else Api.CHUNK_BYTES.toLong()
        val body = SizeLimitedInputStream(call.receiveChannel().toInputStream(), expected)
        val size = try {
            withContext(Dispatchers.IO) { body.use { services.storage.put(chunkName(id, n), it) } }
        } catch (tooBig: PayloadTooLargeException) {
            throw tooLarge(tooBig)
        }
        if (size != expected) {
            withContext(Dispatchers.IO) { services.storage.delete(chunkName(id, n)) }
            throw badRequest()
        }
        services.attachments.markChunk(id, n)
        call.respond(HttpStatusCode.NoContent)
    }

    get("${Api.ATTACHMENTS}/{id}/chunks") {
        val (id, upload) = call.ownUpload(services)
        call.respond(UploadState(services.attachments.receivedChunks(id), upload.complete))
    }

    post("${Api.ATTACHMENTS}/{id}/complete") {
        val (id, upload) = call.ownUpload(services)
        ensure(services.attachments.receivedChunks(id).size == upload.chunks) { badRequest() }
        services.attachments.complete(id)
        call.respond(AttachmentUploaded(id))
    }

    get("${Api.ATTACHMENTS}/{id}") {
        val id = call.parameters["id"].orEmpty()
        ensure(AttachmentId.matches(id)) { notFound() }
        val info = services.attachments.info(id)?.takeIf { it.complete }.orFail { notFound() }
        val range = call.request.header(HttpHeaders.Range)?.let { parseRange(it, info.size) }
        val from = range?.first ?: 0L
        val to = range?.last ?: (info.size - 1)
        val content = withContext(Dispatchers.IO) { openFrom(services.storage, id, info, from) }.orFail { notFound() }
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        if (range != null) {
            call.response.header(HttpHeaders.ContentRange, "bytes $from-$to/${info.size}")
            call.respond(HttpStatusCode.PartialContent, StreamedContent(content, to - from + 1, HttpStatusCode.PartialContent))
        } else {
            call.respond(StreamedContent(content, info.size, HttpStatusCode.OK))
        }
    }
}

/** The caller's own unfinished or finished chunked upload; anyone else's is not theirs to touch. */
private suspend fun ApplicationCall.ownUpload(services: Services): Pair<String, StoredAttachment> {
    val id = parameters["id"].orEmpty()
    ensure(AttachmentId.matches(id)) { notFound() }
    val upload = services.attachments.info(id)?.takeIf { it.chunks > 0 }.orFail { notFound() }
    ensure(upload.owner == account.id) { forbidden() }
    return id to upload
}

/** `bytes=a-b` or `bytes=a-` within [size]; null for anything else, which is answered in full. */
internal fun parseRange(header: String, size: Long): LongRange? {
    val spec = header.removePrefix("bytes=").takeIf { header.startsWith("bytes=") && ',' !in header } ?: return null
    val from = spec.substringBefore('-').toLongOrNull() ?: return null
    val to = spec.substringAfter('-').takeIf { it.isNotEmpty() }?.toLongOrNull() ?: (size - 1)
    if (from < 0 || from >= size || to < from) return null
    return from..minOf(to, size - 1)
}

/** The bytes of an attachment from [from] on; chunks are opened one at a time, as reading reaches them. */
private fun openFrom(storage: AttachmentStorage, id: String, info: StoredAttachment, from: Long): InputStream? {
    if (info.chunks == 0) return storage.open(id)?.content?.also { it.skipNBytes(from) }
    val first = (from / Api.CHUNK_BYTES).toInt()
    val chunks = object : Enumeration<InputStream> {
        var next = first

        override fun hasMoreElements() = next < info.chunks

        override fun nextElement(): InputStream {
            val stream = checkNotNull(storage.open(chunkName(id, next))) { "missing chunk" }.content
            if (next == first) stream.skipNBytes(from - first.toLong() * Api.CHUNK_BYTES)
            next++
            return stream
        }
    }
    return SequenceInputStream(chunks)
}

/** Stops after [limit] bytes, for a range that ends before the file does. */
private class LimitedStream(private val source: InputStream, private var limit: Long) : InputStream() {
    override fun read(): Int {
        if (limit <= 0) return -1
        return source.read().also { if (it >= 0) limit-- }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (limit <= 0) return -1
        val read = source.read(buffer, offset, minOf(length.toLong(), limit).toInt())
        if (read > 0) limit -= read
        return read
    }

    override fun close() = source.close()
}

private class StreamedContent(
    private val content: InputStream,
    length: Long,
    override val status: HttpStatusCode,
) : OutgoingContent.ReadChannelContent() {
    override val contentType = ContentType.Application.OctetStream
    override val contentLength = length

    override fun readFrom(): ByteReadChannel = LimitedStream(content, contentLength).toByteReadChannel(context = Dispatchers.IO)
}
