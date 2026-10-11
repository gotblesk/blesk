package app.blesk.server.attachments

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/**
 * Talks to MinIO over its S3 API with plain HTTP and SigV4, which keeps the whole S3 SDK out of
 * the server. Object ids are URL-safe already, so paths need no extra encoding.
 */
class MinioStorage(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    private val bucket: String,
    private val spoolDirectory: Path = Path.of(System.getProperty("java.io.tmpdir")),
    private val clock: Clock = Clock.systemUTC(),
) : AttachmentStorage {
    private val endpoint = URI.create(endpoint.trimEnd('/'))
    private val signer = SigV4Signer(accessKey, secretKey)
    private val http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    /** Creates the bucket on first start, so the deployment needs no separate setup step. */
    fun ensureBucket() {
        val head = send("HEAD", "/$bucket", HttpRequest.BodyPublishers.noBody(), HttpResponse.BodyHandlers.discarding())
        when (head.statusCode()) {
            HTTP_OK -> Unit
            HTTP_NOT_FOUND -> {
                val created =
                    send("PUT", "/$bucket", HttpRequest.BodyPublishers.noBody(), HttpResponse.BodyHandlers.discarding())
                if (created.statusCode() != HTTP_OK) throw IOException("bucket create failed: ${created.statusCode()}")
            }
            else -> throw IOException("bucket check failed: ${head.statusCode()}")
        }
    }

    override fun put(id: String, content: InputStream): Long {
        // S3 needs the length up front and the upload may be chunked, so it is spooled to disk first.
        val spool = Files.createTempFile(spoolDirectory, "upload", ".part")
        try {
            val size = Files.newOutputStream(spool).use { content.copyTo(it) }
            val response =
                send("PUT", objectPath(id), HttpRequest.BodyPublishers.ofFile(spool), HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() != HTTP_OK) throw IOException("upload failed: ${response.statusCode()}")
            return size
        } finally {
            Files.deleteIfExists(spool)
        }
    }

    override fun open(id: String): StoredObject? {
        val response =
            send("GET", objectPath(id), HttpRequest.BodyPublishers.noBody(), HttpResponse.BodyHandlers.ofInputStream())
        return when (response.statusCode()) {
            HTTP_OK -> StoredObject(response.headers().firstValueAsLong("content-length").orElse(-1), response.body())
            HTTP_NOT_FOUND -> {
                response.body().close()
                null
            }
            else -> {
                response.body().close()
                throw IOException("download failed: ${response.statusCode()}")
            }
        }
    }

    override fun delete(id: String) {
        val response =
            send("DELETE", objectPath(id), HttpRequest.BodyPublishers.noBody(), HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() != HTTP_NO_CONTENT && response.statusCode() != HTTP_OK) {
            throw IOException("delete failed: ${response.statusCode()}")
        }
    }

    private fun objectPath(id: String) = "/$bucket/$id"

    private fun <T> send(
        method: String,
        path: String,
        body: HttpRequest.BodyPublisher,
        handler: HttpResponse.BodyHandler<T>,
    ): HttpResponse<T> {
        val uri = endpoint.resolve(path)
        val now = clock.instant()
        val host = if (uri.port == -1) uri.host else "${uri.host}:${uri.port}"
        val amzDate = SigV4Signer.timestamp(now)
        val signed = mapOf(
            "host" to host,
            "x-amz-content-sha256" to SigV4Signer.UNSIGNED_PAYLOAD,
            "x-amz-date" to amzDate,
        )
        val authorization = signer.authorization(method, uri.rawPath, "", signed, SigV4Signer.UNSIGNED_PAYLOAD, now)
        val request = HttpRequest.newBuilder(uri)
            .method(method, body)
            .header("x-amz-content-sha256", SigV4Signer.UNSIGNED_PAYLOAD)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization)
            .build()
        return http.send(request, handler)
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_NOT_FOUND = 404
    }
}
