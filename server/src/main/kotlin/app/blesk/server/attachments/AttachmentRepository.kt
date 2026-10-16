package app.blesk.server.attachments

import app.blesk.server.db.Database
import app.blesk.server.db.query
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import java.time.Instant

class AttachmentRepository(private val database: Database) {
    suspend fun record(id: String, size: Long, now: Instant) {
        database.transaction { connection ->
            connection.update("INSERT INTO attachments (id, size, created_at) VALUES (?, ?, ?)", id, size, now)
        }
    }

    /**
     * Old attachments, except those a space post still points to (public posts keep their files),
     * and uploads left unfinished since [abandonedBefore]. With each, how many chunk objects it has.
     */
    suspend fun expired(before: Instant, abandonedBefore: Instant, limit: Int): List<Pair<String, Int>> =
        database.transaction { connection ->
            connection.query(
                """
                SELECT a.id, a.chunks FROM attachments a
                WHERE (a.created_at < ? OR (NOT a.complete AND a.created_at < ?))
                  AND NOT EXISTS (SELECT 1 FROM space_posts p WHERE p.attachment_id = a.id)
                ORDER BY a.created_at
                LIMIT ?
                """.trimIndent(),
                before,
                abandonedBefore,
                limit,
            ) { it.getString("id") to it.getInt("chunks") }
        }

    suspend fun startChunked(id: String, owner: Long, size: Long, chunks: Int, now: Instant) {
        database.transaction { connection ->
            connection.update(
                "INSERT INTO attachments (id, size, created_at, chunks, complete, owner_id) VALUES (?, ?, ?, ?, FALSE, ?)",
                id, size, now, chunks, owner,
            )
        }
    }

    suspend fun info(id: String): StoredAttachment? = database.transaction { connection ->
        connection.queryOne("SELECT size, chunks, complete, owner_id FROM attachments WHERE id = ?", id) {
            StoredAttachment(it.getLong("size"), it.getInt("chunks"), it.getBoolean("complete"), it.getLong("owner_id"))
        }
    }

    suspend fun markChunk(id: String, n: Int) {
        database.transaction { connection ->
            connection.update("INSERT INTO attachment_chunks (attachment_id, n) VALUES (?, ?) ON CONFLICT DO NOTHING", id, n)
        }
    }

    suspend fun receivedChunks(id: String): List<Int> = database.transaction { connection ->
        connection.query("SELECT n FROM attachment_chunks WHERE attachment_id = ? ORDER BY n", id) { it.getInt("n") }
    }

    suspend fun complete(id: String) {
        database.transaction { connection -> connection.update("UPDATE attachments SET complete = TRUE WHERE id = ?", id) }
    }

    /** False for an unknown id and for a chunked upload that is still unfinished. */
    suspend fun isComplete(id: String): Boolean = database.transaction { connection ->
        connection.queryOne("SELECT complete FROM attachments WHERE id = ?", id) { it.getBoolean("complete") } ?: false
    }

    suspend fun forget(id: String) {
        database.transaction { connection -> connection.update("DELETE FROM attachments WHERE id = ?", id) }
    }
}

/** An attachment as the server keeps it: [chunks] is 0 for one uploaded in a single request. */
data class StoredAttachment(val size: Long, val chunks: Int, val complete: Boolean, val owner: Long)
