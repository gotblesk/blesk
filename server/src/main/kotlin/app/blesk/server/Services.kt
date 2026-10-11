package app.blesk.server

import app.blesk.server.attachments.AttachmentRepository
import app.blesk.server.attachments.AttachmentStorage
import app.blesk.server.auth.AccountRepository
import app.blesk.server.auth.AuthRepository
import app.blesk.server.bots.BotRepository
import app.blesk.server.bots.BotSignals
import app.blesk.server.bots.BotWebhooks
import app.blesk.server.bots.WebhookPolicy
import app.blesk.server.calls.CallTokens
import app.blesk.server.db.Database
import app.blesk.server.http.RateLimiter
import app.blesk.server.keys.KeyRepository
import app.blesk.server.messages.MessageRepository
import app.blesk.server.socket.SocketHub
import app.blesk.server.spaces.PostRepository
import app.blesk.server.spaces.SpaceRepository
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

/** Everything the routes need, wired once at startup. */
class Services(
    database: Database,
    val storage: AttachmentStorage,
    val authRateLimiter: RateLimiter,
    val postRateLimiter: RateLimiter,
    val trustProxy: Boolean,
    val callRateLimiter: RateLimiter,
    /** Null when no LiveKit server is configured. */
    val callTokens: CallTokens?,
    /** Per person, for creating bots and writing to them. */
    val botRateLimiter: RateLimiter,
    /** Per bot, for every Bot API call. */
    val botApiRateLimiter: RateLimiter,
    webhookPolicy: WebhookPolicy,
    webhookRetryDelays: List<Duration> = BotWebhooks.DefaultRetryDelays,
    private val clock: Clock = Clock.systemUTC(),
) {
    val accounts = AccountRepository(database)
    val auth = AuthRepository(database)
    val keys = KeyRepository(database)
    val messages = MessageRepository(database)
    val attachments = AttachmentRepository(database)
    val spaces = SpaceRepository(database)
    val posts = PostRepository(database)
    val hub = SocketHub()
    val bots = BotRepository(database)
    val botSignals = BotSignals()
    val webhooks = BotWebhooks(webhookPolicy, webhookRetryDelays)

    fun now(): Instant = clock.instant()
}
