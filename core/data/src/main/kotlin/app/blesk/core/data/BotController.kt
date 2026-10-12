package app.blesk.core.data

import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import kotlinx.coroutines.flow.SharedFlow

/** Why creating a bot did not work. */
enum class BotResult { Ok, NameTaken, NameInvalid, TooMany, Failed }

/** A bot you just created or whose token you renewed; the token is shown once. */
data class BotWithToken(val bot: BotInfo, val token: String)

/** Chats with bots and the bots you own. Bot chats are not end-to-end encrypted. */
interface BotController {
    fun isBot(conversationId: String): Boolean

    /** Commands the bot offers, for suggestions as you type "/". */
    suspend fun commands(conversationId: String): List<BotCommand>

    /** Sends a button press under [messageId] back to the bot. */
    fun press(conversationId: String, messageId: String, data: String)

    /** Short notes a bot shows after a button press. */
    val notices: SharedFlow<String>
}

/** The bots you own: creating them and handing their tokens to your program. */
interface BotAccounts {
    /** Null when the server could not be asked, so a failure is never shown as «no bots». */
    suspend fun myBots(): List<BotInfo>?

    suspend fun createBot(username: String, name: String, about: String): Pair<BotResult, BotWithToken?>

    suspend fun renewToken(username: String): BotWithToken?

    suspend fun deleteBot(username: String): Boolean
}
