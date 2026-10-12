package app.blesk.core.network

import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.BotCreated
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallToken
import app.blesk.shared.protocol.CallTokenRequest
import app.blesk.shared.protocol.Calls
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.CreateBot
import app.blesk.shared.protocol.CreateSpace
import app.blesk.shared.protocol.CreateSpaceChannel
import app.blesk.shared.protocol.Discovery
import app.blesk.shared.protocol.EditPost
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.KeyStatus
import app.blesk.shared.protocol.KeyUpload
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.PreKeyBundleDto
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.RoleChange
import app.blesk.shared.protocol.SendResult
import app.blesk.shared.protocol.ServerInfo
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.Spaces
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.TokenResponse
import app.blesk.shared.protocol.UpdateSpace
import app.blesk.shared.protocol.UserToBot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** A request the server refused, with its stable error code. */
class ApiException(val status: Int, val code: String) : Exception("HTTP $status $code")

/**
 * Typed access to the blesk server. A pinned server is trusted only by its public key hash:
 * the certificate chain is not checked against certificate authorities (it is self-signed),
 * but OkHttp's pinner rejects any key other than the one from the QR code.
 */
@Suppress("TooManyFunctions") // one method per endpoint
class BleskApi(val address: ServerAddress, engine: HttpClientEngine? = null) {
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var token: String? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().proxySelector(NetworkProxy.selector).apply { pinTo(address) }.build()
    }

    val client: HttpClient = (engine?.let { HttpClient(it) { configure() } } ?: HttpClient(OkHttp) {
        configure()
        engine { preconfigured = http }
    })

    /** An OkHttp client with the same trust rules and proxy, for libraries that bring their own networking (calls). */
    fun okHttpClient(): OkHttpClient = http.newBuilder().build()

    /** Closes idle connections, so the next request takes the route the proxy setting says now. */
    fun dropConnections() = http.connectionPool.evictAll()

    /** How long the server takes to answer through [proxy], in milliseconds; null if it does not. */
    suspend fun checkThrough(proxy: ProxyConfig): Long? = withContext(Dispatchers.IO) {
        val probe = http.newBuilder()
            .proxy(proxy.toProxy())
            .connectionPool(ConnectionPool())
            .callTimeout(PROXY_CHECK_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        val started = System.nanoTime()
        runCatching {
            probe.newCall(Request.Builder().url(url(Api.INFO)).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false).takeIf { it }?.let { TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) }
    }

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

    suspend fun createBot(request: CreateBot): BotCreated = call { client.post(url(Bots.PATH)) { auth(); jsonBody(request) } }

    suspend fun myBots(): List<BotInfo> = call { client.get(url("${Bots.PATH}/mine")) { auth() } }

    suspend fun renewBotToken(username: String): BotCreated = call { client.post(url("${Bots.userPath(username)}/token")) { auth() } }

    suspend fun deleteBot(username: String) = expect { client.delete(url(Bots.userPath(username))) { auth() } }

    suspend fun bot(username: String): BotInfo? = optional { client.get(url(Bots.userPath(username))) { auth() } }

    suspend fun sendToBot(username: String, update: UserToBot) =
        expect { client.post(url("${Bots.userPath(username)}/updates")) { auth(); jsonBody(update) } }

    suspend fun callToken(room: String): CallToken = call { client.post(url(Calls.TOKEN)) { auth(); jsonBody(CallTokenRequest(room)) } }

    suspend fun register(request: RegisterRequest): AccountInfo = call {
        client.post(url(Api.ACCOUNTS)) { jsonBody(request) }
    }

    suspend fun account(username: String, link: String? = null): AccountInfo? = optional {
        client.get(url("${Api.ACCOUNTS}/$username")) {
            auth()
            link?.let { parameter(Api.LINK, it) }
        }
    }

    suspend fun deleteAccount() {
        val response = client.delete(url(Api.ACCOUNT)) { auth() }
        if (!response.status.isSuccess()) throw response.error()
    }

    suspend fun setDiscoverable(discoverable: Boolean) {
        val response = client.put(url(Api.DISCOVERY)) { auth(); jsonBody(Discovery(discoverable)) }
        if (!response.status.isSuccess()) throw response.error()
    }

    suspend fun challenge(username: String): Challenge = call { client.post(url(Api.CHALLENGE)) { jsonBody(ChallengeRequest(username)) } }

    suspend fun token(request: TokenRequest): TokenResponse = call { client.post(url(Api.TOKEN)) { jsonBody(request) } }

    suspend fun uploadKeys(upload: KeyUpload) {
        val response = client.put(url(Api.KEYS)) { auth(); jsonBody(upload) }
        if (!response.status.isSuccess()) throw response.error()
    }

    suspend fun keyStatus(): KeyStatus = call { client.get(url(Api.KEY_STATUS)) { auth() } }

    suspend fun bundle(username: String, link: String? = null): PreKeyBundleDto? = optional {
        client.get(url("${Api.KEYS}/$username")) {
            auth()
            link?.let { parameter(Api.LINK, it) }
        }
    }

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

    // Public spaces: plain HTTP JSON, not end-to-end encrypted.

    suspend fun createSpace(request: CreateSpace): SpaceInfo = call { client.post(url(Spaces.PATH)) { auth(); jsonBody(request) } }

    suspend fun space(handle: String): SpaceInfo? = optional { client.get(url("${Spaces.PATH}/$handle")) { auth() } }

    suspend fun updateSpace(handle: String, update: UpdateSpace): SpaceInfo =
        call { client.patch(url("${Spaces.PATH}/$handle")) { auth(); jsonBody(update) } }

    suspend fun deleteSpace(handle: String) = expect { client.delete(url("${Spaces.PATH}/$handle")) { auth() } }

    suspend fun searchSpaces(query: String): List<SpaceInfo> =
        call { client.get(url(Spaces.PATH)) { auth(); parameter("query", query) } }

    suspend fun mySpaces(): List<SpaceInfo> = call { client.get(url("${Spaces.PATH}/mine")) { auth() } }

    suspend fun joinSpace(handle: String): SpaceInfo = call { client.post(url("${Spaces.PATH}/$handle/join")) { auth() } }

    suspend fun leaveSpace(handle: String) = expect { client.post(url("${Spaces.PATH}/$handle/leave")) { auth() } }

    suspend fun spaceMembers(handle: String): List<SpaceMember> =
        call { client.get(url("${Spaces.PATH}/$handle/members")) { auth(); parameter("limit", MEMBER_PAGE) } }

    suspend fun setSpaceRole(handle: String, change: RoleChange) =
        expect { client.post(url("${Spaces.PATH}/$handle/roles")) { auth(); jsonBody(change) } }

    suspend fun ban(handle: String, username: String) = expect { client.post(url("${Spaces.PATH}/$handle/ban/$username")) { auth() } }

    suspend fun addSpaceChannel(handle: String, title: String): SpaceInfo =
        call { client.post(url("${Spaces.PATH}/$handle/channels")) { auth(); jsonBody(CreateSpaceChannel(title)) } }

    suspend fun removeSpaceChannel(handle: String, channel: String) =
        expect { client.delete(url("${Spaces.PATH}/$handle/channels/$channel")) { auth() } }

    suspend fun post(handle: String, post: NewPost): Post = call {
        client.post(url("${Spaces.PATH}/$handle/posts")) {
            auth()
            jsonBody(post)
        }
    }

    suspend fun posts(handle: String, channel: String, before: Long? = null): List<Post> = call {
        client.get(url("${Spaces.PATH}/$handle/posts")) {
            auth()
            parameter("channel", channel)
            parameter("limit", POST_PAGE)
            before?.let { parameter("before", it) }
        }
    }

    suspend fun editPost(handle: String, id: Long, text: String): Post =
        call { client.patch(url("${Spaces.PATH}/$handle/posts/$id")) { auth(); jsonBody(EditPost(text)) } }

    suspend fun deletePost(handle: String, id: Long) = expect { client.delete(url("${Spaces.PATH}/$handle/posts/$id")) { auth() } }

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

    private suspend inline fun expect(block: () -> HttpResponse) {
        val response = block()
        if (!response.status.isSuccess()) throw response.error()
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
        const val PROXY_CHECK_MILLIS = 8_000L
        const val POST_PAGE = 50
        const val MEMBER_PAGE = 200
        const val SOCKET_TIMEOUT_MILLIS = 60_000L
    }
}

/** With a pin from the QR code, only that key is trusted; otherwise the system's CAs are. */
private fun OkHttpClient.Builder.pinTo(address: ServerAddress) {
    val pin = address.pin ?: return
    val trust = PinTrustManager(pin)
    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
    sslSocketFactory(ssl.socketFactory, trust)
    // The key pin already proves who is on the other end; a bare IP has no name to check.
    hostnameVerifier { hostname, _ -> hostname == address.host }
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
