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

    /** Old attachments, except those a space post still points to: public posts keep their files. */
    suspend fun expired(before: Instant, limit: Int): List<String> = database.transaction { connection ->
        connection.query(
            """
            SELECT a.id FROM attachments a
            WHERE a.created_at < ? AND NOT EXISTS (SELECT 1 FROM space_posts p WHERE p.attachment_id = a.id)
            ORDER BY a.created_at
            LIMIT ?
            """.trimIndent(),
            before,
            limit,
        ) { it.getString("id") }
    }

    suspend fun exists(id: String): Boolean = database.transaction { connection ->
        connection.queryOne("SELECT 1 FROM attachments WHERE id = ?", id) { true } ?: false
    }

    suspend fun forget(id: String) {
        database.transaction { connection -> connection.update("DELETE FROM attachments WHERE id = ?", id) }
    }
}
