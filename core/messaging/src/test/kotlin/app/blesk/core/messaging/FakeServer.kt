package app.blesk.core.messaging

import app.blesk.core.network.BleskApi
import app.blesk.core.network.ServerAddress
import app.blesk.core.network.Transport
import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.KeyStatus
import app.blesk.shared.protocol.KeyUpload
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.PreKeyBundleDto
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.SendResult
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.ServerInfo
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.TokenResponse
import app.blesk.shared.protocol.UserToBot
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The server contract in memory: enough of it to run two real clients against each other. */
class FakeServer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val accounts = ConcurrentHashMap<String, RegisterRequest>()
    private val keys = ConcurrentHashMap<String, KeyUpload>()
    private val transports = ConcurrentHashMap<String, FakeTransport>()
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    private val ids = AtomicLong()
    private val bots = ConcurrentHashMap<String, BotInfo>()

    /** What people sent to bots, in order: (from, update). */
    val botInbox = java.util.concurrent.CopyOnWriteArrayList<Pair<String, UserToBot>>()

    fun addBot(info: BotInfo) {
        bots[info.username] = info
    }

    /** A bot's program sends [message] to [to]. */
    fun botSays(bot: String, to: String, message: BotMessage) {
        val body = java.util.Base64.getEncoder().encodeToString(json.encodeToString(BotMessage.serializer(), message).encodeToByteArray())
        transport(to).deliver(Envelope(ids.incrementAndGet(), bot, EnvelopeType.BOT, body, System.currentTimeMillis()))
    }

    fun transport(username: String): FakeTransport = transports.getOrPut(username) { FakeTransport() }

    fun api(): BleskApi = BleskApi(ServerAddress("https://blesk.test"), MockEngine { request -> handle(request) })

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        val me = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        return when {
            path == "/v1/info" -> ok(json.encodeToString(ServerInfo.serializer(), ServerInfo()))
            path == "/v1/accounts" && request.method == HttpMethod.Post -> {
                val body = json.decodeFromString(RegisterRequest.serializer(), text(request))
                if (accounts.putIfAbsent(body.username, body) != null) return error(HttpStatusCode.Conflict, "username_taken")
                ok(json.encodeToString(AccountInfo.serializer(), AccountInfo(body.username, body.identityKey)), HttpStatusCode.Created)
            }
            path.startsWith("/v1/accounts/") -> {
                val account = accounts[path.substringAfterLast('/')] ?: return error(HttpStatusCode.NotFound, "not_found")
                ok(json.encodeToString(AccountInfo.serializer(), AccountInfo(account.username, account.identityKey)))
            }
            path == "/v1/auth/challenge" -> ok(json.encodeToString(Challenge.serializer(), Challenge("nonce", Long.MAX_VALUE)))
            path == "/v1/auth/token" -> {
                val username = json.decodeFromString(TokenRequest.serializer(), text(request)).username
                ok(json.encodeToString(TokenResponse.serializer(), TokenResponse(username, Long.MAX_VALUE)))
            }
            path == "/v1/keys" && request.method == HttpMethod.Put -> {
                val upload = json.decodeFromString(KeyUpload.serializer(), text(request))
                keys.merge(me!!, upload) { old, new ->
                    KeyUpload(
                        signedPreKey = new.signedPreKey ?: old.signedPreKey,
                        kyberLastResort = new.kyberLastResort ?: old.kyberLastResort,
                        preKeys = old.preKeys + new.preKeys,
                        kyberPreKeys = old.kyberPreKeys + new.kyberPreKeys,
                    )
                }
                ok(json.encodeToString(KeyStatus.serializer(), KeyStatus(0, 0)))
            }
            path.startsWith("/v1/keys/") -> {
                val user = path.substringAfterLast('/')
                val upload = keys[user] ?: return error(HttpStatusCode.NotFound, "not_found")
                val account = accounts.getValue(user)
                keys[user] = upload.copy(preKeys = upload.preKeys.drop(1), kyberPreKeys = upload.kyberPreKeys.drop(1))
                val bundle = PreKeyBundleDto(
                    username = user,
                    registrationId = account.registrationId,
                    deviceId = 1,
                    identityKey = account.identityKey,
                    signedPreKey = upload.signedPreKey!!,
                    preKey = upload.preKeys.firstOrNull(),
                    kyberPreKey = upload.kyberPreKeys.firstOrNull() ?: upload.kyberLastResort!!,
                )
                ok(json.encodeToString(PreKeyBundleDto.serializer(), bundle))
            }
            path.startsWith("/v1/messages/") -> {
                val message = json.decodeFromString(OutgoingMessage.serializer(), text(request))
                val id = ids.incrementAndGet()
                val envelope = Envelope(id, me!!, message.type, message.body, System.currentTimeMillis())
                transport(path.substringAfterLast('/')).deliver(envelope)
                ok(json.encodeToString(SendResult.serializer(), SendResult(id, envelope.timestampMillis)))
            }
            path.startsWith("/v1/bots/") && path.endsWith("/updates") -> {
                botInbox += me!! to json.decodeFromString(UserToBot.serializer(), text(request))
                ok("{}")
            }
            path.startsWith("/v1/bots/") -> bots[path.substringAfterLast('/')]
                ?.let { ok(json.encodeToString(BotInfo.serializer(), it)) }
                ?: error(HttpStatusCode.NotFound, "not_found")
            path == "/v1/attachments" -> {
                val id = "blob${ids.incrementAndGet()}"
                blobs[id] = bytes(request)
                ok(json.encodeToString(AttachmentUploaded.serializer(), AttachmentUploaded(id)))
            }
            path.startsWith("/v1/attachments/") -> respond(
                ByteReadChannel(blobs.getValue(path.substringAfterLast('/'))),
                HttpStatusCode.OK,
            )
            else -> error(HttpStatusCode.NotFound, "no_route")
        }
    }

    private suspend fun bytes(request: HttpRequestData): ByteArray = when (val body = request.body) {
        is OutgoingContent.ByteArrayContent -> body.bytes()
        is OutgoingContent.ReadChannelContent -> body.readFrom().toByteArray()
        is OutgoingContent.WriteChannelContent -> {
            val channel = io.ktor.utils.io.ByteChannel()
            body.writeTo(channel)
            channel.close()
            channel.toByteArray()
        }
        else -> ByteArray(0)
    }

    private suspend fun text(request: HttpRequestData) = bytes(request).decodeToString()

    private fun MockRequestHandleScope.ok(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String) =
        respond(
            json.encodeToString(ErrorBody.serializer(), ErrorBody(code)),
            status,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
}

class FakeTransport : Transport {
    private val flow = MutableSharedFlow<ServerFrame>(replay = 64, extraBufferCapacity = 64)
    override val frames: SharedFlow<ServerFrame> = flow
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)
    val acked = mutableListOf<Long>()

    fun deliver(envelope: Envelope) {
        flow.tryEmit(ServerFrame.Deliver(envelope))
    }

    override fun start() = Unit

    override fun ack(serverId: Long) {
        synchronized(acked) { acked += serverId }
    }

    override fun stop() = Unit
}
