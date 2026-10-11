package app.blesk.core.network

import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.KeyStatus
import app.blesk.shared.protocol.KeyUpload
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.PreKeyBundleDto
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.SendResult
import app.blesk.shared.protocol.ServerInfo
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** A request the server refused, with its stable error code. */
class ApiException(val status: Int, val code: String) : Exception("HTTP $status $code")

/**
 * Typed access to the blesk server. A pinned server is trusted only by its public key hash:
 * the certificate chain is not checked against certificate authorities (it is self-signed),
 * but OkHttp's pinner rejects any key other than the one from the QR code.
 */
class BleskApi(val address: ServerAddress, engine: HttpClientEngine? = null) {
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var token: String? = null

    val client: HttpClient = (engine?.let { HttpClient(it) { configure() } } ?: HttpClient(OkHttp) {
        configure()
        engine {
            config {
                address.pin?.let { pin ->
                    val trust = PinTrustManager(pin)
                    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
                    sslSocketFactory(ssl.socketFactory, trust)
                    // The key pin already proves who is on the other end; a bare IP has no name to check.
                    hostnameVerifier { hostname, _ -> hostname == address.host }
                }
            }
        }
    })

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }
    }

    suspend fun info(): ServerInfo = call { client.get(url(Api.INFO)) }

    suspend fun register(request: RegisterRequest): AccountInfo = call {
        client.post(url(Api.ACCOUNTS)) { jsonBody(request) }
    }

    suspend fun account(username: String): AccountInfo? = optional { client.get(url("${Api.ACCOUNTS}/$username")) { auth() } }

    suspend fun challenge(username: String): Challenge = call { client.post(url(Api.CHALLENGE)) { jsonBody(ChallengeRequest(username)) } }

    suspend fun token(request: TokenRequest): TokenResponse = call { client.post(url(Api.TOKEN)) { jsonBody(request) } }

    suspend fun uploadKeys(upload: KeyUpload) {
        val response = client.put(url(Api.KEYS)) { auth(); jsonBody(upload) }
        if (!response.status.isSuccess()) throw response.error()
    }

    suspend fun keyStatus(): KeyStatus = call { client.get(url(Api.KEY_STATUS)) { auth() } }

    suspend fun bundle(username: String): PreKeyBundleDto? = optional { client.get(url("${Api.KEYS}/$username")) { auth() } }

    suspend fun send(username: String, message: OutgoingMessage): SendResult = call {
        client.post(url("${Api.MESSAGES}/$username")) { auth(); jsonBody(message) }
    }

    suspend fun uploadAttachment(file: File): AttachmentUploaded = call {
        client.post(url(Api.ATTACHMENTS)) {
            auth()
            setBody(FileContent(file))
        }
    }

    suspend fun downloadAttachment(id: String, output: OutputStream) {
        client.prepareGet(url("${Api.ATTACHMENTS}/$id")) { auth() }.execute { response ->
            if (!response.status.isSuccess()) throw response.error()
            response.bodyAsChannel().copyTo(output)
        }
    }

    fun socketUrl(): String = url(Api.SOCKET).replaceFirst("https://", "wss://")

    internal fun url(path: String) = address.url + path

    internal fun HttpRequestBuilder.auth() {
        token?.let { bearerAuth(it) }
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(value: T) {
        contentType(ContentType.Application.Json)
        setBody(value)
    }

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T {
        val response = block()
        if (!response.status.isSuccess()) throw response.error()
        return response.body()
    }

    private suspend inline fun <reified T> optional(block: () -> HttpResponse): T? {
        val response = block()
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) throw response.error()
        return response.body()
    }

    private suspend fun HttpResponse.error(): ApiException {
        val code = runCatching { body<ErrorBody>().code }.getOrDefault("http_${status.value}")
        return ApiException(status.value, code)
    }

    fun close() = client.close()

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val SOCKET_TIMEOUT_MILLIS = 60_000L
    }
}

/**
 * Trusts exactly one key: the server certificate must carry the public key whose SHA-256
 * (over SubjectPublicKeyInfo, in OkHttp's `sha256/<base64>` form) came from the QR code.
 * Certificate authorities play no part, which is what a self-signed server needs.
 */
internal class PinTrustManager(private val pin: String) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client certificates are not used")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty chain")
        leaf.checkValidity()
        if (spkiPin(leaf) != pin) throw CertificateException("Server key does not match the pinned key")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun spkiPin(certificate: X509Certificate): String =
            "sha256/" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded))
    }
}

/** Streams a file as the request body without loading it into memory. */
private class FileContent(private val file: File) : OutgoingContent.ReadChannelContent() {
    override val contentType: ContentType = ContentType.Application.OctetStream
    override val contentLength: Long = file.length()

    override fun readFrom(): ByteReadChannel = file.inputStream().toByteReadChannel()
}
