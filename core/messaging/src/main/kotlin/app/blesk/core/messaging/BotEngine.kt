package app.blesk.core.messaging

import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ContactEntity
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.BotMessage
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallbackPress
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.InlineButton
import app.blesk.shared.protocol.UserToBot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.builtins.ListSerializer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Chats with bots. They go through the server in the clear, so they live next to the encrypted
 * chats but never share their machinery: no Signal session, no receipts, no typing.
 */
internal class BotEngine(
    private val db: BleskDatabase,
    private val api: BleskApi,
    private val me: String,
    private val clock: () -> Long,
    private val onIncoming: (bot: String, title: String, text: String) -> Unit = { _, _, _ -> },
) {
    private val noticeFlow = MutableSharedFlow<String>(extraBufferCapacity = NOTICE_BUFFER)
    val notices: SharedFlow<String> = noticeFlow.asSharedFlow()
    private val commandCache = ConcurrentHashMap<String, List<BotCommand>>()

    fun isBot(conversationId: String) = Bots.isValidUsername(conversationId)

    /** Finds a bot by its username and opens the chat with it. */
    suspend fun open(username: String): String? {
        val info = runCatching { api.bot(username) }.getOrNull() ?: return null
        remember(info)
        return info.username
    }

    suspend fun commands(username: String): List<BotCommand> {
        commandCache[username]?.let { return it }
        val info = runCatching { api.bot(username) }.getOrNull() ?: return emptyList()
        remember(info)
        return info.commands
    }

    suspend fun send(username: String, text: String, replyToId: String?) {
        val id = UUID.randomUUID().toString()
        val now = clock()
        db.messages().upsert(
            MessageEntity(id, username, me, text, now, SendStatus.Sending.name, replyToId = replyToId),
        )
        db.conversations().setPreview(username, me, text, now)
        val sent = runCatching { api.sendToBot(username, UserToBot(id, text = text.take(Bots.MAX_TEXT))) }.isSuccess
        if (sent) {
            db.messages().advanceStatus(listOf(id), me, SendStatus.Sent)
        } else {
            db.messages().setStatus(listOf(id), me, SendStatus.Failed.name)
        }
    }

    suspend fun press(username: String, messageId: String, data: String): Boolean =
        runCatching {
            api.sendToBot(username, UserToBot(UUID.randomUUID().toString(), callback = CallbackPress(messageId, data)))
        }.isSuccess

    /** Stores what a bot sent; returns false if the envelope was not a readable bot message. */
    suspend fun receive(envelope: Envelope): Boolean {
        if (!isBot(envelope.sender)) return false
        // Like every envelope body, a bot message travels as Base64; here it is plain JSON inside.
        val message = runCatching {
            storageJson.decodeFromString(BotMessage.serializer(), Base64.getDecoder().decode(envelope.body).decodeToString())
        }.getOrNull() ?: return false
        val bot = envelope.sender
        if (message.text.isEmpty()) {
            message.notice?.let { noticeFlow.tryEmit(it) }
            return true
        }
        val buttons = encodeButtons(message.buttons)
        val target = message.editOf
        if (target != null) {
            val id = messageId(bot, target)
            if (db.messages().editText(id, bot, message.text.take(Bots.MAX_TEXT)) > 0) db.messages().setButtons(id, buttons)
            return true
        }
        ensure(bot)
        val inserted = db.messages().insert(
            MessageEntity(
                id = messageId(bot, message.id),
                conversationId = bot,
                authorId = bot,
                text = message.text.take(Bots.MAX_TEXT),
                sentAt = envelope.timestampMillis,
                status = SendStatus.Delivered.name,
                buttons = buttons,
            ),
        )
        if (inserted != -1L) {
            db.conversations().incrementUnread(bot)
            db.conversations().setPreview(bot, bot, message.text, envelope.timestampMillis)
            onIncoming(bot, db.conversations().get(bot)?.title ?: bot, message.text)
        }
        message.notice?.let { noticeFlow.tryEmit(it) }
        return true
    }

    /** The id the bot gave a message, from the id it has here. */
    fun botMessageId(bot: String, localId: String) = localId.removePrefix("$bot$SEPARATOR")

    private suspend fun ensure(bot: String) {
        if (db.conversations().get(bot) != null) return
        val info = runCatching { api.bot(bot) }.getOrNull() ?: BotInfo(bot, bot)
        remember(info)
    }

    private suspend fun remember(info: BotInfo) {
        commandCache[info.username] = info.commands
        db.contacts().insertIfAbsent(ContactEntity(info.username, info.name))
        db.contacts().setName(info.username, info.name)
        val fresh = ConversationEntity(
            id = info.username,
            kind = ConversationKind.Bot.name,
            title = info.name,
            protection = Protection.Open.name,
            peer = info.username,
            updatedAt = clock(),
            previewText = "",
        )
        db.conversations().insertIfAbsent(fresh)
        db.conversations().setTitle(info.username, info.name)
    }

    private fun messageId(bot: String, id: String) = "$bot$SEPARATOR$id"

    companion object {
        private const val SEPARATOR = ':'
        private const val NOTICE_BUFFER = 4

        /** Only buttons that do exactly one thing, and links only over https. */
        fun encodeButtons(rows: List<List<InlineButton>>): String? {
            val clean = rows.take(Bots.MAX_BUTTON_ROWS).map { row ->
                row.take(Bots.MAX_BUTTONS_IN_ROW).mapNotNull { button ->
                    val link = button.url?.takeIf { it.startsWith("https://") }
                    when {
                        button.data != null && link == null -> BotButton(button.text.take(MAX_BUTTON_TEXT), data = button.data)
                        link != null && button.data == null -> BotButton(button.text.take(MAX_BUTTON_TEXT), url = link)
                        else -> null
                    }
                }
            }.filter { it.isNotEmpty() }
            return if (clean.isEmpty()) null else storageJson.encodeToString(ListSerializer(ListSerializer(BotButton.serializer())), clean)
        }

        private const val MAX_BUTTON_TEXT = 64
    }
}
