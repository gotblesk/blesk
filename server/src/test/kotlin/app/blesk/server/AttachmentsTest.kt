package app.blesk.server

import app.blesk.server.attachments.PayloadTooLargeException
import app.blesk.server.attachments.SizeLimitedInputStream
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
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
    fun `size limit stops reading past the limit`() {
        val limited = SizeLimitedInputStream(ByteArray(11).inputStream(), limit = 10)
        assertThrows(PayloadTooLargeException::class.java) { limited.readBytes() }
        assertEquals(10, SizeLimitedInputStream(ByteArray(10).inputStream(), limit = 10).readBytes().size)
    }
}
