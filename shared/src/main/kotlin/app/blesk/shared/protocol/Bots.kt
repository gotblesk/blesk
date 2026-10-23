package app.blesk.shared.protocol

import kotlinx.serialization.Serializable

/**
 * Bots are accounts run by programs. Chats with bots are NOT end-to-end encrypted: the server
 * reads them to pass them on, and the app shows them matte and labelled.
 *
 * People talk to a bot through [USER_PATH]; the bot's program talks through the Bot API under
 * [API_PATH] with its token as a Bearer token, by long polling or by a webhook. What the bot
 * sends reaches people as envelopes of type [EnvelopeType.BOT] whose body is a [BotMessage] in JSON.
 */
object Bots {
    /** Owner side: create, list, rotate the token, delete. */
    const val PATH = "/v1/bots"

    /** People side: `POST /v1/bots/{username}/updates` with a [UserToBot]; `GET /v1/bots/{username}` gives [BotInfo]. */
    fun userPath(username: String) = "$PATH/$username"

    /** Bot side, authorised by the bot token: `getMe`, `getUpdates`, `sendMessage`, ... see docs/BOT_API.md. */
    const val API_PATH = "/bot/v1"

    /** Bot usernames end with this, so people always know they are talking to a program. */
    const val SUFFIX = "_bot"

    private val pattern = Regex("^[a-z0-9_]{1,28}_bot$")

    fun isValidUsername(username: String) = pattern.matches(username)

    /** A new bot may not be named like the service, «blesk_bot» or «support_bot»; existing bots keep their names. */
    fun isReservedName(username: String) = Usernames.isReserved(username.removeSuffix(SUFFIX))

    const val MAX_TEXT = 4096
    const val MAX_BUTTON_ROWS = 8
    const val MAX_BUTTONS_IN_ROW = 4
    const val MAX_CALLBACK_DATA = 64
    const val MAX_COMMANDS = 50
    const val MAX_BOTS_PER_OWNER = 20

    /** Seconds a getUpdates call may wait for something new. */
    const val MAX_POLL_SECONDS = 50

    /** The header carrying the webhook secret the bot chose. */
    const val WEBHOOK_SECRET_HEADER = "X-Blesk-Bot-Secret"
}

// Owner side

@Serializable
data class CreateBot(val username: String, val name: String, val about: String = "")

/** [token] is shown once; the server keeps only its hash. */
@Serializable
data class BotCreated(val bot: BotInfo, val token: String)

@Serializable
data class BotCommand(val command: String, val description: String)

/** What anyone can see about a bot. */
@Serializable
data class BotInfo(
    val username: String,
    val name: String,
    val about: String = "",
    val commands: List<BotCommand> = emptyList(),
)

// People side

/** A message or a button press from a person to a bot. Exactly one of [text] and [callback] is set. */
@Serializable
data class UserToBot(
    /** Chosen by the sender; repeating it is ignored. */
    val id: String,
    val text: String? = null,
    val callback: CallbackPress? = null,
)

@Serializable
data class CallbackPress(val messageId: String, val data: String)

/** What a bot sends to a person: a new message, or a replacement of [editOf]. */
@Serializable
data class BotMessage(
    val id: String,
    val text: String,
    val buttons: List<List<InlineButton>> = emptyList(),
    /** Id of an earlier message of this bot that this one replaces. */
    val editOf: String? = null,
    /** A short note shown to the person after pressing a button, not stored in the chat. */
    val notice: String? = null,
)

/** A button under a bot message: either sends [data] back to the bot or opens [url]. */
@Serializable
data class InlineButton(val text: String, val data: String? = null, val url: String? = null)

// Bot side (Bot API)

@Serializable
data class Update(
    val updateId: Long,
    val message: IncomingMessage? = null,
    val callback: IncomingCallback? = null,
)

@Serializable
data class IncomingMessage(val id: String, val from: String, val text: String, val dateMillis: Long)

@Serializable
data class IncomingCallback(val id: String, val from: String, val messageId: String, val data: String)

@Serializable
data class SendMessage(
    /** The person's username. */
    val chat: String,
    val text: String,
    val buttons: List<List<InlineButton>> = emptyList(),
)

@Serializable
data class EditMessage(val chat: String, val messageId: String, val text: String, val buttons: List<List<InlineButton>> = emptyList())

@Serializable
data class AnswerCallback(val callbackId: String, val notice: String? = null)

@Serializable
data class SetWebhook(val url: String, val secret: String? = null)

@Serializable
data class SetCommands(val commands: List<BotCommand>)

@Serializable
data class SentMessage(val messageId: String)
