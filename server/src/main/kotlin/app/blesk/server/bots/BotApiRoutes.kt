package app.blesk.server.bots

import app.blesk.server.BleskJson
import app.blesk.server.Services
import app.blesk.server.auth.Tokens
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.ensure
import app.blesk.server.http.forbidden
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.rateLimited
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.AnswerCallback
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.EditMessage
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.InlineButton
import app.blesk.shared.protocol.SendMessage
import app.blesk.shared.protocol.SentMessage
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SetCommands
import app.blesk.shared.protocol.SetWebhook
import app.blesk.shared.protocol.Update
import app.blesk.shared.protocol.Usernames
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

const val BOT_BEARER = "bot-bearer"

private val log = LoggerFactory.getLogger("blesk.bots")

private const val UPDATES_PAGE = 100
private const val MAX_BUTTON_TEXT = 64
private const val MAX_BUTTON_URL = 512
private const val MAX_NOTICE = 200
private const val MAX_COMMAND_DESCRIPTION = 256
private const val MAX_SECRET = 256
private val CommandPattern = Regex("^[a-z0-9_]{1,32}$")
private val SecretPattern = Regex("^[A-Za-z0-9_-]+$")

/**
 * Accepts `Authorization: Bearer <bot token>`. A custom provider because the token contains a colon,
 * which the stock Bearer parser rejects. The token is never taken from the URL, so it cannot end up in
 * proxy or access logs.
 */
private class BotTokenProvider(private val services: Services) : AuthenticationProvider(Config(BOT_BEARER)) {
    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith(SCHEME, ignoreCase = true) }
            ?.substring(SCHEME.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val bot = token?.let { services.bots.byTokenHash(Tokens.hash(it)) }
        if (bot != null) {
            context.principal(bot)
            return
        }
        val cause = if (token == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
        context.challenge(BOT_BEARER, cause) { challenge, call ->
            call.respond(HttpStatusCode.Unauthorized, ErrorBody(ErrorCodes.UNAUTHORIZED))
            challenge.complete()
        }
    }

    private companion object {
        const val SCHEME = "Bearer "
    }
}

fun AuthenticationConfig.botBearer(services: Services) {
    register(BotTokenProvider(services))
}

/** The bot whose token authorised the call, after its rate limit; only inside `authenticate(BOT_BEARER)`. */
private fun ApplicationCall.bot(services: Services): Bot {
    val bot = checkNotNull(principal<Bot>()) { "route is not authenticated as a bot" }
    ensure(services.botApiRateLimiter.tryAcquire(bot.id.toString())) { rateLimited(services.botApiRateLimiter) }
    return bot
}

fun Route.botApiRoutes(services: Services) = route(Bots.API_PATH) {
    get("getMe") { call.respond(call.bot(services).info()) }

    get("getUpdates") {
        val bot = call.bot(services)
        val offset = call.request.queryParameters["offset"]?.let { it.toLongOrNull().orFail { badRequest() } } ?: 0L
        val timeout = call.request.queryParameters["timeout"]?.let { it.toIntOrNull().orFail { badRequest() } } ?: 0
        ensure(timeout in 0..Bots.MAX_POLL_SECONDS) { badRequest() }
        val deadline = TimeSource.Monotonic.markNow() + timeout.seconds
        while (true) {
            val version = services.botSignals.version(bot.id)
            val updates = services.bots.pending(bot.id, offset, UPDATES_PAGE)
            val remaining = -deadline.elapsedNow()
            if (updates.isNotEmpty() || !remaining.isPositive()) {
                call.respond(updates)
                return@get
            }
            services.botSignals.awaitAfter(bot.id, version, remaining)
        }
    }

    post("sendMessage") {
        val bot = call.bot(services)
        val request = call.receiveJson<SendMessage>()
        validate(request.text, request.buttons)
        val chatId = chatOf(services, bot, request.chat)
        val messageId = Tokens.newId()
        services.bots.rememberMessage(bot.id, messageId, chatId, services.now())
        services.sendAsBot(bot, chatId, BotMessage(messageId, request.text, request.buttons))
        call.respond(SentMessage(messageId))
    }

    post("editMessage") {
        val bot = call.bot(services)
        val request = call.receiveJson<EditMessage>()
        validate(request.text, request.buttons)
        val chatId = chatOf(services, bot, request.chat)
        ensure(services.bots.messageChat(bot.id, request.messageId) == chatId) { notFound() }
        // The replacement keeps the original id, so later edits and button presses keep referring to the same message.
        val edit = BotMessage(request.messageId, request.text, request.buttons, editOf = request.messageId)
        services.sendAsBot(bot, chatId, edit)
        call.respond(SentMessage(request.messageId))
    }

    post("answerCallback") {
        val bot = call.bot(services)
        val request = call.receiveJson<AnswerCallback>()
        val notice = request.notice
        ensure(notice == null || notice.length in 1..MAX_NOTICE) { badRequest(ErrorCodes.INVALID_TEXT) }
        val presser = services.bots.answerCallback(bot.id, request.callbackId)
        if (presser == null) {
            // A second answer to the same press is ignored rather than shown twice.
            ensure(services.bots.callbackExists(bot.id, request.callbackId)) { notFound() }
        } else if (notice != null) {
            services.sendAsBot(bot, presser, BotMessage(request.callbackId, text = "", notice = notice))
        }
        call.respond(HttpStatusCode.NoContent)
    }

    post("setWebhook") {
        val bot = call.bot(services)
        val request = call.receiveJson<SetWebhook>()
        ensure(services.webhooks.allows(request.url)) { badRequest(ErrorCodes.INVALID_WEBHOOK) }
        val secret = request.secret
        ensure(secret == null || (secret.length <= MAX_SECRET && SecretPattern.matches(secret))) {
            badRequest(ErrorCodes.INVALID_WEBHOOK)
        }
        services.bots.setWebhook(bot.id, Webhook(request.url, secret))
        call.respond(HttpStatusCode.NoContent)
    }

    post("deleteWebhook") {
        services.bots.setWebhook(call.bot(services).id, null)
        call.respond(HttpStatusCode.NoContent)
    }

    post("setCommands") {
        val bot = call.bot(services)
        val request = call.receiveJson<SetCommands>()
        ensure(isValid(request.commands)) { badRequest(ErrorCodes.INVALID_COMMANDS) }
        services.bots.setCommands(bot.id, request.commands)
        call.respond(HttpStatusCode.NoContent)
    }
}

/** The person's account id, if they ever wrote to this bot; a bot cannot start a chat by itself. */
private suspend fun chatOf(services: Services, bot: Bot, username: String): Long {
    ensure(Usernames.isWellFormed(username)) { notFound() }
    val person = services.accounts.find(username).orFail { notFound() }
    ensure(services.bots.hasChat(bot.id, person.id)) { forbidden(ErrorCodes.CHAT_NOT_STARTED) }
    return person.id
}

private fun validate(text: String, buttons: List<List<InlineButton>>) {
    ensure(isValidText(text)) { badRequest(ErrorCodes.INVALID_TEXT) }
    val shapeValid = buttons.size <= Bots.MAX_BUTTON_ROWS && buttons.all { it.size in 1..Bots.MAX_BUTTONS_IN_ROW }
    ensure(shapeValid && buttons.all { row -> row.all(::isValid) }) { badRequest(ErrorCodes.INVALID_BUTTONS) }
}

private fun isValid(button: InlineButton): Boolean {
    if (button.text.isBlank() || button.text.length > MAX_BUTTON_TEXT) return false
    val data = button.data
    val url = button.url
    return when {
        data != null && url == null -> data.length in 1..Bots.MAX_CALLBACK_DATA
        url != null && data == null -> isHttpsLink(url)
        else -> false
    }
}

private fun isHttpsLink(url: String): Boolean {
    if (url.length > MAX_BUTTON_URL) return false
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return false
    }
    return uri.scheme == "https" && !uri.host.isNullOrEmpty()
}

private fun isValid(commands: List<BotCommand>): Boolean =
    commands.size <= Bots.MAX_COMMANDS &&
        commands.distinctBy { it.command }.size == commands.size &&
        commands.all {
            CommandPattern.matches(it.command) && it.description.isNotBlank() && it.description.length <= MAX_COMMAND_DESCRIPTION
        }

/** Stored in the person's queue like any envelope, so it reaches them later if they are offline now. */
internal suspend fun Services.sendAsBot(bot: Bot, recipientId: Long, message: BotMessage) {
    val body = BleskJson.encodeToString(BotMessage.serializer(), message).encodeToByteArray()
    val envelope = messages.storeFromBot(bot.id, bot.username, recipientId, EnvelopeType.BOT, body, now())
    hub.push(recipientId, ServerFrame.Deliver(envelope))
}

/** Hands a new update to the bot's webhook in the background, or wakes its long polls. */
internal fun dispatchUpdate(scope: CoroutineScope, services: Services, bot: Bot, update: Update) {
    val webhook = bot.webhook
    if (webhook == null) {
        services.botSignals.signal(bot.id)
        return
    }
    scope.launch {
        val delivered = try {
            services.webhooks.deliver(webhook, update)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Whatever went wrong, the update stays queued for getUpdates.
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            log.warn("Webhook delivery failed: {}", failure.javaClass.name)
            false
        }
        if (delivered) services.bots.markConsumed(bot.id, update.updateId) else services.botSignals.signal(bot.id)
    }
}
