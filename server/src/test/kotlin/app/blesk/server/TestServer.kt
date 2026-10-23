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
import app.blesk.shared.protocol.UsernameCheck
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** One real Postgres for the whole test run; every test uses fresh random usernames instead of a clean database. */
object TestDatabase {
    private val postgres: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().start().also { postgres -> Runtime.getRuntime().addShutdownHook(Thread { postgres.close() }) }
    }

    val dataSource: DataSource by lazy { postgres.postgresDatabase.also(Database::migrate) }

    /** An empty database of its own, for tests of what the whole server shares, such as bootstrap invites. */
    fun fresh(): DataSource = postgres.getDatabase(USER, create()).also(Database::migrate)

    /** Like [fresh], but not migrated yet: for the server's own process, which migrates as it starts. */
    fun freshUrl(): String = postgres.getJdbcUrl(USER, create())

    private fun create(): String {
        val name = "blesk_" + UUID.randomUUID().toString().replace("-", "")
        postgres.postgresDatabase.connection.use { it.createStatement().use { statement -> statement.execute("CREATE DATABASE $name") } }
        return name
    }

    const val USER = "postgres"
}

/** A clock moved by hand, for what expires. */
class TestClock(@Volatile var instant: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)) : Clock() {
    fun advance(by: Duration) {
        instant += by.toJavaDuration()
    }

    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}

class TestUser(val username: String, val identity: IdentityKeyPair, val token: String)

class Harness(val client: HttpClient, val services: Services) {
    suspend fun register(
        username: String = randomUsername(),
        identity: IdentityKeyPair = IdentityKeyPair.generate(),
        invite: String? = null,
    ): TestUser {
        val response = registerRaw(username, identity, invite = invite)
        assertEquals(HttpStatusCode.Created, response.status)
        return TestUser(username, identity, login(username, identity))
    }

    suspend fun registerRaw(
        username: String,
        identity: IdentityKeyPair,
        signed: String = username,
        invite: String? = null,
    ): HttpResponse = client.post(Api.ACCOUNTS) {
        contentType(ContentType.Application.Json)
        setBody(
            RegisterRequest(
                username = username,
                identityKey = identity.publicKey.serialize().b64(),
                registrationId = Random.nextInt(1, 16380),
                signature = identity.privateKey.calculateSignature(Api.registrationProof(signed)).b64(),
                invite = invite,
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

    /** The name check, anonymous unless a [token] is given. */
    suspend fun checkUsernameRaw(name: String, token: String? = null): HttpResponse =
        client.get("${Api.USERNAMES}/$name") { token?.let(::bearerAuth) }

    suspend fun checkUsername(name: String, token: String? = null): UsernameCheck {
        val response = checkUsernameRaw(name, token)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
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
    requestRateLimit: RateLimitConfig = RateLimitConfig(capacity = 100_000, refillPerMinute = 100_000),
    usernameCheckRateLimit: RateLimitConfig = RateLimitConfig(capacity = 100_000, refillPerMinute = 100_000),
    // Open, so that tests of everything else register without invites.
    registration: RegistrationMode = RegistrationMode.Open,
    clock: Clock = Clock.systemUTC(),
    dataSource: DataSource = TestDatabase.dataSource,
    block: suspend ApplicationTestBuilder.(Harness) -> Unit,
) {
    // Every test uploads a few megabytes; left behind, they fill the disk across runs.
    val attachments = Files.createTempDirectory("blesk-attachments")
    try {
        val services = Services(
            database = Database(dataSource),
            storage = FileSystemStorage(attachments),
            requestRateLimiter = RateLimiter(requestRateLimit),
            authRateLimiter = RateLimiter(rateLimit),
            usernameCheckLimiter = RateLimiter(usernameCheckRateLimit),
            postRateLimiter = RateLimiter(postRateLimit),
            trustProxy = false,
            callRateLimiter = RateLimiter(callRateLimit),
            callTokens = liveKit?.let(::CallTokens),
            botRateLimiter = RateLimiter(botRateLimit),
            botApiRateLimiter = RateLimiter(botRateLimit),
            // Webhook tests call a local HTTP server; production never allows private addresses.
            webhookPolicy = WebhookPolicy(allowPrivateAddresses = true),
            registration = registration,
            webhookRetryDelays = listOf(ZERO, 100.milliseconds),
            clock = clock,
        )
        testApplication {
            application { bleskModule(services, maintenanceInterval = 1.hours) }
            val client = createClient {
                install(ContentNegotiation) { json(BleskJson) }
                install(WebSockets)
            }
            block(Harness(client, services))
        }
    } finally {
        attachments.toFile().deleteRecursively()
    }
}

fun randomUsername(): String = "u_" + randomNamePart(12)

/** Without b, e, i, k, l, o, s and the digits read as them: behind its first letter, a random name is never a reserved one. */
fun randomNamePart(length: Int): String = (1..length).map { UNRESERVED_ALPHABET.random() }.joinToString("")

private const val UNRESERVED_ALPHABET = "acdfghjmnpqrtuvwxyz2689"

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
