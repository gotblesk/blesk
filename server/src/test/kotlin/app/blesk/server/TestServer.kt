package app.blesk.server

import app.blesk.server.attachments.FileSystemStorage
import app.blesk.server.bots.WebhookPolicy
import app.blesk.server.calls.CallTokens
import app.blesk.server.db.Database
import app.blesk.server.http.RateLimiter
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.KeyUpload
import app.blesk.shared.protocol.OneTimeKey
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SignedKey
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.sql.DataSource
import kotlin.random.Random
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** One real Postgres for the whole test run; every test uses fresh random usernames instead of a clean database. */
object TestDatabase {
    val dataSource: DataSource by lazy {
        val postgres = EmbeddedPostgres.builder().start()
        Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
        postgres.postgresDatabase.also(Database::migrate)
    }
}

class TestUser(val username: String, val identity: IdentityKeyPair, val token: String)

class Harness(val client: HttpClient, val services: Services) {
    suspend fun register(username: String = randomUsername(), identity: IdentityKeyPair = IdentityKeyPair.generate()): TestUser {
        val response = registerRaw(username, identity)
        assertEquals(HttpStatusCode.Created, response.status)
        return TestUser(username, identity, login(username, identity))
    }

    suspend fun registerRaw(username: String, identity: IdentityKeyPair, signed: String = username): HttpResponse =
        client.post(Api.ACCOUNTS) {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    username = username,
                    identityKey = identity.publicKey.serialize().b64(),
                    registrationId = Random.nextInt(1, 16380),
                    signature = identity.privateKey.calculateSignature(Api.registrationProof(signed)).b64(),
                ),
            )
        }

    suspend fun challenge(username: String): Challenge = client.post(Api.CHALLENGE) {
        contentType(ContentType.Application.Json)
        setBody(ChallengeRequest(username))
    }.body()

    suspend fun tokenRaw(username: String, nonce: String, signer: IdentityKeyPair): HttpResponse = client.post(Api.TOKEN) {
        contentType(ContentType.Application.Json)
        setBody(TokenRequest(username, nonce, signer.privateKey.calculateSignature(Api.authProof(nonce)).b64()))
    }

    suspend fun login(username: String, identity: IdentityKeyPair): String {
        val response = tokenRaw(username, challenge(username).nonce, identity)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<TokenResponse>().token
    }

    suspend fun uploadKeys(user: TestUser, upload: KeyUpload): HttpResponse = client.put(Api.KEYS) {
        bearerAuth(user.token)
        contentType(ContentType.Application.Json)
        setBody(upload)
    }

    fun keys(user: TestUser, preKeys: Int, kyberPreKeys: Int, firstId: Int = 1): KeyUpload {
        val signer = user.identity.privateKey
        fun signedKem(id: Int): SignedKey {
            val key = KEMKeyPair.generate(KEMKeyType.KYBER_1024).publicKey.serialize()
            return SignedKey(id, key.b64(), signer.calculateSignature(key).b64())
        }
        val signedPreKey = ECKeyPair.generate().publicKey.serialize()
        return KeyUpload(
            signedPreKey = SignedKey(1, signedPreKey.b64(), signer.calculateSignature(signedPreKey).b64()),
            kyberLastResort = signedKem(LAST_RESORT_ID),
            preKeys = (firstId until firstId + preKeys).map { OneTimeKey(it, ECKeyPair.generate().publicKey.serialize().b64()) },
            kyberPreKeys = (firstId until firstId + kyberPreKeys).map(::signedKem),
        )
    }

    suspend fun accountId(user: TestUser): Long = checkNotNull(services.accounts.find(user.username)).id

    companion object {
        const val LAST_RESORT_ID = 9_999
    }
}

fun serverTest(
    rateLimit: RateLimitConfig = RateLimitConfig(capacity = 100_000, refillPerMinute = 100_000),
    postRateLimit: RateLimitConfig = RateLimitConfig(capacity = 100_000, refillPerMinute = 100_000),
    callRateLimit: RateLimitConfig = RateLimitConfig(capacity = 100_000, refillPerMinute = 100_000),
    liveKit: LiveKitConfig? = null,
    botRateLimit: RateLimitConfig = RateLimitConfig(capacity = 100_000, refillPerMinute = 100_000),
    block: suspend ApplicationTestBuilder.(Harness) -> Unit,
) {
    // Every test uploads a few megabytes; left behind, they fill the disk across runs.
    val attachments = Files.createTempDirectory("blesk-attachments")
    try {
        serverTest(attachments, rateLimit, postRateLimit, callRateLimit, liveKit, botRateLimit, block)
    } finally {
        attachments.toFile().deleteRecursively()
    }
}

@Suppress("LongParameterList")
private fun serverTest(
    attachments: Path,
    rateLimit: RateLimitConfig,
    postRateLimit: RateLimitConfig,
    callRateLimit: RateLimitConfig,
    liveKit: LiveKitConfig?,
    botRateLimit: RateLimitConfig,
    block: suspend ApplicationTestBuilder.(Harness) -> Unit,
) = testApplication {
    val services = Services(
        database = Database(TestDatabase.dataSource),
        storage = FileSystemStorage(attachments),
        authRateLimiter = RateLimiter(rateLimit),
        postRateLimiter = RateLimiter(postRateLimit),
        trustProxy = false,
        callRateLimiter = RateLimiter(callRateLimit),
        callTokens = liveKit?.let(::CallTokens),
        botRateLimiter = RateLimiter(botRateLimit),
        botApiRateLimiter = RateLimiter(botRateLimit),
        // Webhook tests call a local HTTP server; production never allows private addresses.
        webhookPolicy = WebhookPolicy(allowPrivateAddresses = true),
        webhookRetryDelays = listOf(ZERO, 100.milliseconds),
    )
    application { bleskModule(services, maintenanceInterval = 1.hours) }
    val client = createClient {
        install(ContentNegotiation) { json(BleskJson) }
        install(WebSockets)
    }
    block(Harness(client, services))
}

fun randomUsername(): String = "u_" + (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

suspend fun WebSocketSession.nextFrame(): ServerFrame = withTimeout(10.seconds) {
    var frame = incoming.receive()
    while (frame !is Frame.Text) frame = incoming.receive()
    BleskJson.decodeFromString(ServerFrame.serializer(), frame.readText())
}

/** Polls until [check] stops throwing, for effects that happen after a socket frame is processed. */
suspend fun eventually(check: suspend () -> Unit) {
    withTimeout(10.seconds) {
        while (true) {
            try {
                check()
                return@withTimeout
            } catch (_: AssertionError) {
                delay(20)
            }
        }
    }
}
