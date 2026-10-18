package app.blesk.core.messaging

import app.blesk.core.network.BleskApi
import app.blesk.core.network.Link
import app.blesk.core.network.ServerAddress
import app.blesk.core.network.Transport
import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.AttachmentUploaded
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList
import app.blesk.shared.protocol.Invites
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
import app.blesk.shared.protocol.UploadStart
import app.blesk.shared.protocol.UploadStarted
import app.blesk.shared.protocol.UploadState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.signal.libsignal.protocol.IdentityKey
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** The server contract in memory: enough of it to run two real clients against each other. */
class FakeServer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val accounts = ConcurrentHashMap<String, RegisterRequest>()
    private val keys = ConcurrentHashMap<String, KeyUpload>()
    private val transports = ConcurrentHashMap<String, FakeTransport>()
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    private val uploads = ConcurrentHashMap<String, Upload>()
    private val ids = AtomicLong()
    private val bots = ConcurrentHashMap<String, BotInfo>()

    /** Registration by invite, as with `BLESK_REGISTRATION=invite`; open by default, like most tests want. */
    @Volatile
    var inviteOnly = false
    private val invites = ConcurrentHashMap<String, FakeInvite>()

    /** An invite of the server owner: it belongs to no account. */
    fun bootstrapInvite(): String = newInvite(owner = null)

    /** How many sign-in challenges still fail, as when the connection drops right after an account is made. */
    @Volatile
    var droppedChallenges = 0

    /** How many sign-in challenges are answered with something that is not a challenge, as by a broken proxy. */
    @Volatile
    var garbledChallenges = 0

    private val refusals = ConcurrentHashMap<String, ConcurrentLinkedQueue<Refusal>>()

    /** Sign-in tokens handed out so far. */
    val tokensIssued = AtomicInteger()

    /** While set, file downloads wait for it to complete: the moment a file is on its way, held open. */
    @Volatile
    var heldDownloads: CompletableDeferred<Unit>? = null

    /** How many downloads have come to [heldDownloads] and waited there. */
    val downloadsHeld = AtomicInteger()

    /** How many refusals have come to their [Refusal.answerAfter] and waited there. */
    val refusalsHeld = AtomicInteger()

    /**
     * A refusal of a message: the status the server answers with and the Retry-After it names, if any. Without a
     * status the connection breaks before any answer. With [answerAfter] the answer waits for it: the moment a
     * refused request is on its way back, held open.
     */
    class Refusal(
        val status: HttpStatusCode?,
        val retryAfterSeconds: Long? = null,
        val answerAfter: CompletableDeferred<Unit>? = null,
    )

    /** The next messages [from] sends are refused, one refusal each, in order; later ones go through. */
    fun refuseSends(from: String, vararg refused: Refusal) {
        refusals.getOrPut(from) { ConcurrentLinkedQueue() } += refused
    }

    /** What people sent to bots, in order: (from, update). */
    val botInbox = java.util.concurrent.CopyOnWriteArrayList<Pair<String, UserToBot>>()

    fun addBot(info: BotInfo) {
        bots[info.username] = info
    }

    /** A bot's program sends [message] to [to]. */
    fun botSays(bot: String, to: String, message: BotMessage) {
        val body = Base64.getEncoder().encodeToString(json.encodeToString(BotMessage.serializer(), message).encodeToByteArray())
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
            path == "/v1/accounts" && request.method == HttpMethod.Post ->
                register(json.decodeFromString(RegisterRequest.serializer(), text(request)))
            path == "/v1/invites" -> me?.let { invites(it, request.method) } ?: error(HttpStatusCode.Unauthorized, "unauthorized")
            path.startsWith("/v1/accounts/") -> {
                val account = accounts[path.substringAfterLast('/')] ?: return error(HttpStatusCode.NotFound, "not_found")
                ok(json.encodeToString(AccountInfo.serializer(), AccountInfo(account.username, account.identityKey)))
            }
            path == "/v1/auth/challenge" -> challenge()
            path == "/v1/auth/token" -> token(json.decodeFromString(TokenRequest.serializer(), text(request)))
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
                me?.let { refusals[it]?.poll() }?.let { return refuse(it) }
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
            path == "/v1/attachments/uploads" -> {
                val start = json.decodeFromString(UploadStart.serializer(), text(request))
                val id = "blob${ids.incrementAndGet()}"
                uploads[id] = Upload(start.size, sortedMapOf())
                ok(json.encodeToString(UploadStarted.serializer(), UploadStarted(id, CHUNK)))
            }
            path.contains("/chunks/") -> {
                val id = path.substringAfter("/v1/attachments/").substringBefore('/')
                uploads.getValue(id).chunks[path.substringAfterLast('/').toInt()] = bytes(request)
                respond("", HttpStatusCode.NoContent)
            }
            path.endsWith("/chunks") -> {
                val upload = uploads.getValue(path.substringAfter("/v1/attachments/").substringBefore('/'))
                ok(json.encodeToString(UploadState.serializer(), UploadState(upload.chunks.keys.toList(), false)))
            }
            path.endsWith("/complete") -> {
                val id = path.substringAfter("/v1/attachments/").substringBefore('/')
                blobs[id] = uploads.remove(id)!!.chunks.values.fold(ByteArray(0)) { all, chunk -> all + chunk }
                ok(json.encodeToString(AttachmentUploaded.serializer(), AttachmentUploaded(id)))
            }
            path.startsWith("/v1/attachments/") -> {
                heldDownloads?.let { gate ->
                    downloadsHeld.incrementAndGet()
                    gate.await()
                }
                val blob = blobs.getValue(path.substringAfterLast('/'))
                val from = request.headers[HttpHeaders.Range]?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull()
                if (from == null) {
                    respond(ByteReadChannel(blob), HttpStatusCode.OK)
                } else {
                    respond(
                        ByteReadChannel(blob.copyOfRange(from, blob.size)),
                        HttpStatusCode.PartialContent,
                        headersOf(HttpHeaders.ContentRange, "bytes $from-${blob.size - 1}/${blob.size}"),
                    )
                }
            }
            else -> error(HttpStatusCode.NotFound, "no_route")
        }
    }

    private fun MockRequestHandleScope.challenge(): HttpResponseData {
        if (droppedChallenges > 0) {
            droppedChallenges--
            return error(HttpStatusCode.ServiceUnavailable, "unavailable")
        }
        if (garbledChallenges > 0) {
            garbledChallenges--
            return ok("{}")
        }
        return ok(json.encodeToString(Challenge.serializer(), Challenge("nonce", Long.MAX_VALUE)))
    }

    /** As on the real server, only the identity key an account was made with signs in to it; the token is its name. */
    private fun MockRequestHandleScope.token(request: TokenRequest): HttpResponseData {
        val account = accounts[request.username] ?: return error(HttpStatusCode.Unauthorized, "auth_failed")
        val key = IdentityKey(Base64.getDecoder().decode(account.identityKey)).publicKey
        if (!key.verifySignature(Api.authProof(request.nonce), Base64.getDecoder().decode(request.signature))) {
            return error(HttpStatusCode.Unauthorized, "auth_failed")
        }
        tokensIssued.incrementAndGet()
        return ok(json.encodeToString(TokenResponse.serializer(), TokenResponse(request.username, Long.MAX_VALUE)))
    }

    private fun MockRequestHandleScope.register(body: RegisterRequest): HttpResponseData {
        val invite = body.invite?.let(invites::get)
        if (inviteOnly && body.invite == null) return error(HttpStatusCode.Forbidden, "invite_required")
        if (inviteOnly && (invite == null || invite.usedBy != null)) return error(HttpStatusCode.Forbidden, "invite_invalid")
        // A taken name leaves the invite as it was, so it can be used again with another name.
        if (accounts.putIfAbsent(body.username, body) != null) return error(HttpStatusCode.Conflict, "username_taken")
        if (inviteOnly) invite?.usedBy = body.username
        return ok(json.encodeToString(AccountInfo.serializer(), AccountInfo(body.username, body.identityKey)), HttpStatusCode.Created)
    }

    private fun MockRequestHandleScope.invites(me: String, method: HttpMethod): HttpResponseData {
        val left = Invites.LIMIT - invites.values.count { it.owner == me && it.usedBy == null }
        if (method == HttpMethod.Get) {
            val mine = invites.filterValues { it.owner == me }
                .map { (code, invite) -> invite.info(code) }
                .sortedByDescending { it.createdAtMillis }
            return ok(json.encodeToString(InviteList.serializer(), InviteList(mine, left)))
        }
        if (left <= 0) return error(HttpStatusCode.Conflict, "invite_limit")
        val code = newInvite(me)
        return ok(json.encodeToString(InviteInfo.serializer(), invites.getValue(code).info(code)), HttpStatusCode.Created)
    }

    private fun newInvite(owner: String?): String {
        val code = (1..Invites.CODE_LENGTH).map { Invites.ALPHABET.random() }.joinToString("")
        invites[code] = FakeInvite(owner, createdAt = ids.incrementAndGet())
        return code
    }

    private class FakeInvite(val owner: String?, val createdAt: Long) {
        @Volatile
        var usedBy: String? = null

        fun info(code: String) = InviteInfo(code, createdAt, createdAt + INVITE_TTL, usedBy, usedBy?.let { createdAt })
    }

    private class Upload(val size: Long, val chunks: java.util.SortedMap<Int, ByteArray>)

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

    private suspend fun MockRequestHandleScope.refuse(refusal: Refusal): HttpResponseData {
        refusal.answerAfter?.let { gate ->
            refusalsHeld.incrementAndGet()
            gate.await()
        }
        val status = refusal.status ?: throw IOException("connection reset")
        val headers = listOfNotNull(
            HttpHeaders.ContentType to listOf("application/json"),
            refusal.retryAfterSeconds?.let { HttpHeaders.RetryAfter to listOf(it.toString()) },
        )
        val body = json.encodeToString(ErrorBody.serializer(), ErrorBody("refused"))
        return respond(body, status, headersOf(*headers.toTypedArray()))
    }
}

class FakeTransport : Transport {
    // Like the server, which keeps every frame until it is acknowledged, nothing is ever dropped:
    // a bounded buffer would lose frames whenever the client is slower than the sender.
    private val flow = MutableSharedFlow<ServerFrame>(replay = Int.MAX_VALUE)
    override val frames: SharedFlow<ServerFrame> = flow
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)
    override val link: StateFlow<Link> = MutableStateFlow(Link.Online)
    private val lock = Any()
    private val delivered = mutableListOf<Long>()
    private val acknowledged = mutableListOf<Long>()

    /** Server ids the client has acknowledged, that is, handled: shown, stored or refused. */
    val acked: List<Long> get() = synchronized(lock) { acknowledged.toList() }

    /** Whether the client has handled every frame delivered so far: an ack of one frame says nothing about the next. */
    val caughtUp: Boolean get() = synchronized(lock) { acknowledged.containsAll(delivered) }

    fun deliver(envelope: Envelope) {
        synchronized(lock) { delivered += envelope.serverId }
        flow.tryEmit(ServerFrame.Deliver(envelope))
    }

    override fun start() = Unit

    override fun ack(serverId: Long) {
        synchronized(lock) { acknowledged += serverId }
    }

    override fun stop() = Unit
}

/** Small chunks, so even a test photo goes up in several pieces. */
private const val CHUNK = 64 * 1024

private const val INVITE_TTL = 7L * 24 * 60 * 60 * 1000
