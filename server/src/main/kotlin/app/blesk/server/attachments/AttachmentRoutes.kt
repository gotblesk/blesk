package app.blesk.server.attachments

import app.blesk.server.Services
import app.blesk.server.auth.Tokens
import app.blesk.server.http.ensure
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.tooLarge
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AttachmentId = Regex("^[A-Za-z0-9_-]{22}$")

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

    get("${Api.ATTACHMENTS}/{id}") {
        val id = call.parameters["id"].orEmpty()
        ensure(AttachmentId.matches(id)) { notFound() }
        val stored = withContext(Dispatchers.IO) { services.storage.open(id) }.orFail { notFound() }
        call.respond(StreamedContent(stored))
    }
}

private class StreamedContent(private val stored: StoredObject) : OutgoingContent.ReadChannelContent() {
    override val contentType = ContentType.Application.OctetStream
    override val contentLength = stored.size.takeIf { it >= 0 }

    override fun readFrom(): ByteReadChannel = stored.content.toByteReadChannel(context = Dispatchers.IO)
}
