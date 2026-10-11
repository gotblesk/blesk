package app.blesk.server.bots

import app.blesk.server.BleskJson
import app.blesk.server.db.Database
import app.blesk.server.db.query
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.Update
import kotlinx.serialization.builtins.ListSerializer
import java.sql.ResultSet
import java.time.Instant

class Bot(
    val id: Long,
    val username: String,
    val ownerId: Long,
    val name: String,
    val about: String,
    val commands: List<BotCommand>,
    val webhook: Webhook?,
) {
    fun info() = BotInfo(username, name, about, commands)
}

class Webhook(val url: String, val secret: String?) {
    override fun toString() = "Webhook(secret=${if (secret == null) "none" else "set"})"
}

enum class BotCreation { Created, Taken, TooMany }

/** A person's message or button press as stored for the bot; [isNew] is false for a repeated client id. */
class EnqueuedUpdate(val update: Update, val isNew: Boolean)

private val CommandList = ListSerializer(BotCommand.serializer())

class BotRepository(private val database: Database) {
    /** [Taken] also covers a person's account with the same name, created before such names were reserved. */
    suspend fun create(
        username: String,
        ownerId: Long,
        name: String,
        about: String,
        tokenHash: ByteArray,
        now: Instant,
    ): BotCreation = database.transaction { connection ->
        // Serialises creations by one owner, so two parallel requests cannot both pass the limit.
        connection.queryOne("SELECT id FROM accounts WHERE id = ? FOR UPDATE", ownerId) { it.getLong("id") }
        val owned = connection.queryOne("SELECT count(*) AS n FROM bots WHERE owner_id = ?", ownerId) { it.getInt("n") } ?: 0
        if (owned >= Bots.MAX_BOTS_PER_OWNER) return@transaction BotCreation.TooMany
        val personHasIt = connection.queryOne("SELECT 1 AS x FROM accounts WHERE username = ?", username) { true } != null
        if (personHasIt) return@transaction BotCreation.Taken
        val inserted = connection.update(
            """
            INSERT INTO bots (username, owner_id, name, about, token_hash, created_at) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (username) DO NOTHING
            """.trimIndent(),
            username,
            ownerId,
            name,
            about,
            tokenHash,
            now,
        )
        if (inserted == 1) BotCreation.Created else BotCreation.Taken
    }

    suspend fun find(username: String): Bot? = database.transaction { connection ->
        connection.queryOne("SELECT $BOT_COLUMNS FROM bots WHERE username = ?", username, map = ::bot)
    }

    suspend fun byTokenHash(tokenHash: ByteArray): Bot? = database.transaction { connection ->
        connection.queryOne("SELECT $BOT_COLUMNS FROM bots WHERE token_hash = ?", tokenHash, map = ::bot)
    }

    suspend fun owned(ownerId: Long): List<Bot> = database.transaction { connection ->
        connection.query("SELECT $BOT_COLUMNS FROM bots WHERE owner_id = ? ORDER BY username", ownerId, map = ::bot)
    }

    suspend fun replaceToken(botId: Long, tokenHash: ByteArray) {
        database.transaction { it.update("UPDATE bots SET token_hash = ? WHERE id = ?", tokenHash, botId) }
    }

    suspend fun delete(botId: Long) {
        database.transaction { it.update("DELETE FROM bots WHERE id = ?", botId) }
    }

    suspend fun setCommands(botId: Long, commands: List<BotCommand>) {
        database.transaction {
            it.update("UPDATE bots SET commands = ? WHERE id = ?", BleskJson.encodeToString(CommandList, commands), botId)
        }
    }

    suspend fun setWebhook(botId: Long, webhook: Webhook?) {
        database.transaction {
            it.update("UPDATE bots SET webhook_url = ?, webhook_secret = ? WHERE id = ?", webhook?.url, webhook?.secret, botId)
        }
    }

    /**
     * Stores what a person sent and remembers that they started a chat with the bot. A repeated
     * [clientId] from the same person stores nothing and returns the earlier update.
     */
    suspend fun enqueue(
        botId: Long,
        fromId: Long,
        clientId: String,
        callbackId: String?,
        update: Update,
        now: Instant,
    ): EnqueuedUpdate = database.transaction { connection ->
        connection.update(
            "INSERT INTO bot_chats (bot_id, account_id, started_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            botId,
            fromId,
            now,
        )
        val id = connection.queryOne(
            """
            INSERT INTO bot_updates (bot_id, from_id, client_id, callback_id, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (bot_id, from_id, client_id) DO NOTHING
            RETURNING id
            """.trimIndent(),
            botId,
            fromId,
            clientId,
            callbackId,
            BleskJson.encodeToString(Update.serializer(), update),
            now,
        ) { it.getLong("id") }
        if (id != null) {
            EnqueuedUpdate(update.copy(updateId = id), isNew = true)
        } else {
            val earlier = connection.queryOne(
                "SELECT id, payload FROM bot_updates WHERE bot_id = ? AND from_id = ? AND client_id = ?",
                botId,
                fromId,
                clientId,
                map = ::update,
            )
            EnqueuedUpdate(checkNotNull(earlier), isNew = false)
        }
    }

    /** Acknowledges everything before [offset], then returns what is still pending from [offset] on, oldest first. */
    suspend fun pending(botId: Long, offset: Long, limit: Int): List<Update> = database.transaction { connection ->
        connection.update("UPDATE bot_updates SET consumed = TRUE WHERE bot_id = ? AND id < ? AND NOT consumed", botId, offset)
        connection.query(
            "SELECT id, payload FROM bot_updates WHERE bot_id = ? AND id >= ? AND NOT consumed ORDER BY id LIMIT ?",
            botId,
            offset,
            limit,
            map = ::update,
        )
    }

    suspend fun markConsumed(botId: Long, updateId: Long) {
        database.transaction { it.update("UPDATE bot_updates SET consumed = TRUE WHERE bot_id = ? AND id = ?", botId, updateId) }
    }

    suspend fun hasChat(botId: Long, accountId: Long): Boolean = database.transaction { connection ->
        connection.queryOne("SELECT 1 AS x FROM bot_chats WHERE bot_id = ? AND account_id = ?", botId, accountId) { true } != null
    }

    suspend fun rememberMessage(botId: Long, messageId: String, chatId: Long, now: Instant) {
        database.transaction {
            it.update(
                "INSERT INTO bot_messages (bot_id, message_id, chat_id, created_at) VALUES (?, ?, ?, ?)",
                botId,
                messageId,
                chatId,
                now,
            )
        }
    }

    /** The person a bot message went to, or null when the bot never sent it. */
    suspend fun messageChat(botId: Long, messageId: String): Long? = database.transaction { connection ->
        connection.queryOne("SELECT chat_id FROM bot_messages WHERE bot_id = ? AND message_id = ?", botId, messageId) {
            it.getLong("chat_id")
        }
    }

    /** Marks the button press answered and returns who pressed it; null when unknown or already answered. */
    suspend fun answerCallback(botId: Long, callbackId: String): Long? = database.transaction { connection ->
        connection.queryOne(
            "UPDATE bot_updates SET answered = TRUE WHERE bot_id = ? AND callback_id = ? AND NOT answered RETURNING from_id",
            botId,
            callbackId,
        ) { it.getLong("from_id") }
    }

    suspend fun callbackExists(botId: Long, callbackId: String): Boolean = database.transaction { connection ->
        connection.queryOne("SELECT 1 AS x FROM bot_updates WHERE bot_id = ? AND callback_id = ?", botId, callbackId) { true } != null
    }

    suspend fun deleteOlderThan(consumedBefore: Instant, pendingBefore: Instant, messagesBefore: Instant) {
        database.transaction { connection ->
            connection.update("DELETE FROM bot_updates WHERE consumed AND created_at < ?", consumedBefore)
            connection.update("DELETE FROM bot_updates WHERE created_at < ?", pendingBefore)
            connection.update("DELETE FROM bot_messages WHERE created_at < ?", messagesBefore)
        }
    }

    private fun bot(row: ResultSet): Bot {
        val url = row.getString("webhook_url")
        return Bot(
            id = row.getLong("id"),
            username = row.getString("username"),
            ownerId = row.getLong("owner_id"),
            name = row.getString("name"),
            about = row.getString("about"),
            commands = BleskJson.decodeFromString(CommandList, row.getString("commands")),
            webhook = url?.let { Webhook(it, row.getString("webhook_secret")) },
        )
    }

    private fun update(row: ResultSet): Update =
        BleskJson.decodeFromString(Update.serializer(), row.getString("payload")).copy(updateId = row.getLong("id"))

    private companion object {
        const val BOT_COLUMNS = "id, username, owner_id, name, about, commands, webhook_url, webhook_secret"
    }
}
