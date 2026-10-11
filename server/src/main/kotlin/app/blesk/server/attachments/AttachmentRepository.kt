package app.blesk.server.attachments

import app.blesk.server.db.Database
import app.blesk.server.db.query
import app.blesk.server.db.update
import java.time.Instant

class AttachmentRepository(private val database: Database) {
    suspend fun record(id: String, size: Long, now: Instant) {
        database.transaction { connection ->
            connection.update("INSERT INTO attachments (id, size, created_at) VALUES (?, ?, ?)", id, size, now)
        }
    }

    suspend fun expired(before: Instant, limit: Int): List<String> = database.transaction { connection ->
        connection.query("SELECT id FROM attachments WHERE created_at < ? ORDER BY created_at LIMIT ?", before, limit) {
            it.getString("id")
        }
    }

    suspend fun forget(id: String) {
        database.transaction { connection -> connection.update("DELETE FROM attachments WHERE id = ?", id) }
    }
}
