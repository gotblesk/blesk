package app.blesk.server.auth

import app.blesk.server.db.Database
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import java.time.Instant

class Account(val id: Long, val username: String, val identityKey: ByteArray, val registrationId: Int)

class AccountRepository(private val database: Database) {
    /** False when the username is already taken. */
    suspend fun create(username: String, identityKey: ByteArray, registrationId: Int, now: Instant): Boolean =
        database.transaction { connection ->
            connection.update(
                """
                INSERT INTO accounts (username, identity_key, registration_id, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (username) DO NOTHING
                """.trimIndent(),
                username,
                identityKey,
                registrationId,
                now,
            ) == 1
        }

    suspend fun find(username: String): Account? = database.transaction { connection ->
        connection.queryOne(
            "SELECT id, username, identity_key, registration_id FROM accounts WHERE username = ?",
            username,
        ) { row ->
            Account(row.getLong("id"), row.getString("username"), row.getBytes("identity_key"), row.getInt("registration_id"))
        }
    }
}
