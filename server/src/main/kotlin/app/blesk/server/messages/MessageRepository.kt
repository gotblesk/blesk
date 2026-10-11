package app.blesk.server.messages

import app.blesk.server.db.Database
import app.blesk.server.db.instant
import app.blesk.server.db.query
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.SendResult
import java.time.Instant
import java.util.Base64

sealed interface Stored {
    /** A new message; it should be pushed to the recipient's sockets. */
    data class Queued(val envelope: Envelope) : Stored

    /** The sender already sent this client id; nothing new was stored. */
    data class Duplicate(val result: SendResult) : Stored
}

class MessageRepository(private val database: Database) {
    suspend fun store(
        senderId: Long,
        sender: String,
        recipientId: Long,
        clientId: String,
        type: Int,
        body: ByteArray,
        now: Instant,
    ): Stored = database.transaction { connection ->
        val serverId = connection.queryOne(
            """
            INSERT INTO sent_messages (sender_id, client_id, server_id, sent_at)
            VALUES (?, ?, nextval('message_ids'), ?)
            ON CONFLICT (sender_id, client_id) DO NOTHING
            RETURNING server_id
            """.trimIndent(),
            senderId,
            clientId,
            now,
        ) { it.getLong("server_id") }
        if (serverId == null) {
            val previous = connection.queryOne(
                "SELECT server_id, sent_at FROM sent_messages WHERE sender_id = ? AND client_id = ?",
                senderId,
                clientId,
            ) { SendResult(it.getLong("server_id"), it.instant("sent_at").toEpochMilli()) }
            return@transaction Stored.Duplicate(checkNotNull(previous))
        }
        connection.update(
            "INSERT INTO queued_messages (id, recipient_id, sender_id, type, body, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            serverId,
            recipientId,
            senderId,
            type,
            body,
            now,
        )
        Stored.Queued(Envelope(serverId, sender, type, Base64.getEncoder().encodeToString(body), now.toEpochMilli()))
    }

    /** A bot message for [recipientId]; bots need no client ids because the server names their messages. */
    suspend fun storeFromBot(botId: Long, bot: String, recipientId: Long, type: Int, body: ByteArray, now: Instant): Envelope =
        database.transaction { connection ->
            val serverId = checkNotNull(
                connection.queryOne(
                    """
                    INSERT INTO queued_messages (id, recipient_id, sender_bot_id, type, body, created_at)
                    VALUES (nextval('message_ids'), ?, ?, ?, ?, ?)
                    RETURNING id
                    """.trimIndent(),
                    recipientId,
                    botId,
                    type,
                    body,
                    now,
                ) { it.getLong("id") },
            )
            Envelope(serverId, bot, type, Base64.getEncoder().encodeToString(body), now.toEpochMilli())
        }

    suspend fun nextServerId(): Long = database.transaction { connection ->
        checkNotNull(connection.queryOne("SELECT nextval('message_ids') AS id") { it.getLong("id") })
    }

    /** One page of the recipient's queue, oldest first, strictly after [afterId]. */
    suspend fun queued(recipientId: Long, afterId: Long, limit: Int): List<Envelope> = database.transaction { connection ->
        connection.query(
            """
            SELECT q.id, COALESCE(a.username, b.username) AS username, q.type, q.body, q.created_at
            FROM queued_messages q
            LEFT JOIN accounts a ON a.id = q.sender_id
            LEFT JOIN bots b ON b.id = q.sender_bot_id
            WHERE q.recipient_id = ? AND q.id > ?
            ORDER BY q.id
            LIMIT ?
            """.trimIndent(),
            recipientId,
            afterId,
            limit,
        ) { row ->
            Envelope(
                serverId = row.getLong("id"),
                sender = row.getString("username"),
                type = row.getInt("type"),
                body = Base64.getEncoder().encodeToString(row.getBytes("body")),
                timestampMillis = row.instant("created_at").toEpochMilli(),
            )
        }
    }

    suspend fun acknowledge(recipientId: Long, serverId: Long) {
        database.transaction { connection ->
            connection.update("DELETE FROM queued_messages WHERE id = ? AND recipient_id = ?", serverId, recipientId)
        }
    }

    suspend fun deleteOlderThan(queuedBefore: Instant, sentBefore: Instant) {
        database.transaction { connection ->
            connection.update("DELETE FROM queued_messages WHERE created_at < ?", queuedBefore)
            connection.update("DELETE FROM sent_messages WHERE sent_at < ?", sentBefore)
        }
    }
}
