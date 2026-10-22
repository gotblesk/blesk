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
import app.blesk.server.db.DatabaseHealth
import app.blesk.server.http.RateLimiter
import app.blesk.server.invites.InviteRepository
import app.blesk.server.keys.KeyRepository
import app.blesk.server.messages.MessageRepository
import app.blesk.server.socket.SocketHub
import app.blesk.server.spaces.PostRepository
import app.blesk.server.spaces.SpaceRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration

/** Everything the routes need, wired once at startup. */
class Services(
    database: Database,
    val storage: AttachmentStorage,
    /** Per client address, for every request but /health. */
    val requestRateLimiter: RateLimiter,
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
    val registration: RegistrationMode,
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
    val invites = InviteRepository(database)
    val health = DatabaseHealth(database)

    /**
     * One-time keys one person may take from another, keyed "taker:owner": a few at once (a first
     * message, a reset session), then one an hour. Past that the bundle goes out without them, so
     * asking again and again cannot empty someone's one-time keys.
     */
    val oneTimeKeyBudget =
        RateLimiter(capacity = ONE_TIME_KEYS_AT_ONCE, refillPerMinute = 1.0 / MINUTES_PER_ONE_TIME_KEY)

    /**
     * Lookups by @username per person: enough for anyone opening chats by hand, too few to walk the
     * names of everyone on the server.
     */
    val lookupBudget = RateLimiter(capacity = LOOKUPS_AT_ONCE, refillPerMinute = LOOKUPS_PER_MINUTE)

    /** Whole milliseconds: what goes into the database reads back exactly the same (Postgres keeps microseconds and rounds). */
    fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)
}

private const val ONE_TIME_KEYS_AT_ONCE = 5
private const val MINUTES_PER_ONE_TIME_KEY = 60
private const val LOOKUPS_AT_ONCE = 20
private const val LOOKUPS_PER_MINUTE = 10.0
