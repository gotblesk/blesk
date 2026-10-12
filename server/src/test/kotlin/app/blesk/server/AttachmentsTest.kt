package app.blesk.server

import app.blesk.server.attachments.PayloadTooLargeException
import app.blesk.server.attachments.SizeLimitedInputStream
import app.blesk.server.attachments.parseRange
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.UploadStart
import app.blesk.shared.protocol.UploadStarted
import app.blesk.shared.protocol.UploadState
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AttachmentsTest {
    @Test
    fun `uploads and downloads encrypted bytes unchanged`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val bytes = Random.nextBytes(3 * 1024 * 1024 + 17)

        val upload = harness.client.post(Api.ATTACHMENTS) {
            bearerAuth(alice.token)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        assertEquals(HttpStatusCode.OK, upload.status)
        val id = upload.body<AttachmentUploaded>().id
        assertTrue(Regex("^[A-Za-z0-9_-]{22}$").matches(id))

        val download = harness.client.get("${Api.ATTACHMENTS}/$id") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.OK, download.status)
        assertArrayEquals(bytes, download.readRawBytes())
    }

    @Test
    fun `unknown or malformed ids are not found`() = serverTest { harness ->
        val alice = harness.register()
        val unknown = harness.client.get("${Api.ATTACHMENTS}/AAAAAAAAAAAAAAAAAAAAAA") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.ATTACHMENTS}/..%2F..%2Fetc") { bearerAuth(alice.token) }.status)
    }

    @Test
    fun `uploads without a token are refused`() = serverTest { harness ->
        val response = harness.client.post(Api.ATTACHMENTS) { setBody(ByteArray(10)) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `chunked upload resumes, completes and serves ranges`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val bytes = Random.nextBytes(Api.CHUNK_BYTES * 2 + 1234)
        val started = harness.client.post(Api.UPLOADS) {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(UploadStart(bytes.size.toLong()))
        }.body<UploadStarted>()
        fun chunk(n: Int) = bytes.copyOfRange(n * started.chunkBytes, minOf(bytes.size, (n + 1) * started.chunkBytes))

        // Chunk 1 arrives first, as after a dropped connection; the state says what is still missing.
        assertEquals(HttpStatusCode.NoContent, harness.client.put("${Api.ATTACHMENTS}/${started.id}/chunks/1") {
            bearerAuth(alice.token)
            setBody(chunk(1))
        }.status)
        val state = harness.client.get("${Api.ATTACHMENTS}/${started.id}/chunks") { bearerAuth(alice.token) }.body<UploadState>()
        assertEquals(listOf(1), state.received)
        val early = harness.client.post("${Api.ATTACHMENTS}/${started.id}/complete") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.BadRequest, early.status)
        // Someone else cannot write into it, and a short chunk is refused.
        assertEquals(HttpStatusCode.Forbidden, harness.client.put("${Api.ATTACHMENTS}/${started.id}/chunks/0") {
            bearerAuth(bob.token)
            setBody(chunk(0))
        }.status)
        assertEquals(HttpStatusCode.BadRequest, harness.client.put("${Api.ATTACHMENTS}/${started.id}/chunks/0") {
            bearerAuth(alice.token)
            setBody(chunk(0).copyOf(10))
        }.status)
        listOf(0, 2).forEach { n ->
            harness.client.put("${Api.ATTACHMENTS}/${started.id}/chunks/$n") {
                bearerAuth(alice.token)
                setBody(chunk(n))
            }
        }
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.ATTACHMENTS}/${started.id}") { bearerAuth(bob.token) }.status)
        assertEquals(HttpStatusCode.OK, harness.client.post("${Api.ATTACHMENTS}/${started.id}/complete") { bearerAuth(alice.token) }.status)

        assertArrayEquals(bytes, harness.client.get("${Api.ATTACHMENTS}/${started.id}") { bearerAuth(bob.token) }.readRawBytes())
        val from = started.chunkBytes - 100
        val partial = harness.client.get("${Api.ATTACHMENTS}/${started.id}") {
            bearerAuth(bob.token)
            header(HttpHeaders.Range, "bytes=$from-${from + 299}")
        }
        assertEquals(HttpStatusCode.PartialContent, partial.status)
        assertEquals("bytes $from-${from + 299}/${bytes.size}", partial.headers[HttpHeaders.ContentRange])
        assertArrayEquals(bytes.copyOfRange(from, from + 300), partial.readRawBytes())
        val tail = harness.client.get("${Api.ATTACHMENTS}/${started.id}") {
            bearerAuth(bob.token)
            header(HttpHeaders.Range, "bytes=${bytes.size - 5}-")
        }
        assertArrayEquals(bytes.copyOfRange(bytes.size - 5, bytes.size), tail.readRawBytes())
    }

    @Test
    fun `ranges that make no sense are answered in full`() {
        assertEquals(10L..19L, parseRange("bytes=10-19", 100))
        assertEquals(90L..99L, parseRange("bytes=90-", 100))
        assertEquals(90L..99L, parseRange("bytes=90-500", 100))
        assertEquals(null, parseRange("bytes=100-", 100))
        assertEquals(null, parseRange("bytes=1-2,5-6", 100))
        assertEquals(null, parseRange("items=1-2", 100))
    }

    @Test
    fun `size limit stops reading past the limit`() {
        val limited = SizeLimitedInputStream(ByteArray(11).inputStream(), limit = 10)
        assertThrows(PayloadTooLargeException::class.java) { limited.readBytes() }
        assertEquals(10, SizeLimitedInputStream(ByteArray(10).inputStream(), limit = 10).readBytes().size)
    }
}
