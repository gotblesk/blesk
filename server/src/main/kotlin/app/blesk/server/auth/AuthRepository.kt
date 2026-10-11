package app.blesk.server.auth

import app.blesk.server.db.Database
import app.blesk.server.db.instant
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import java.time.Instant

data class AccountPrincipal(val id: Long, val username: String)

class AuthRepository(private val database: Database) {
    suspend fun saveChallenge(nonce: String, username: String, expiresAt: Instant) {
        database.transaction { connection ->
            connection.update("INSERT INTO auth_challenges (nonce, username, expires_at) VALUES (?, ?, ?)", nonce, username, expiresAt)
        }
    }

    /** Deletes the challenge as it is read, so a nonce can be redeemed at most once. Returns its expiry. */
    suspend fun consumeChallenge(nonce: String, username: String): Instant? = database.transaction { connection ->
        connection.queryOne(
            "DELETE FROM auth_challenges WHERE nonce = ? AND username = ? RETURNING expires_at",
            nonce,
            username,
        ) { it.instant("expires_at") }
    }

    suspend fun saveToken(tokenHash: ByteArray, accountId: Long, expiresAt: Instant) {
        database.transaction { connection ->
            connection.update(
                "INSERT INTO auth_tokens (token_hash, account_id, expires_at) VALUES (?, ?, ?)",
                tokenHash,
                accountId,
                expiresAt,
            )
        }
    }

    suspend fun principal(tokenHash: ByteArray, now: Instant): AccountPrincipal? = database.transaction { connection ->
        connection.queryOne(
            """
            SELECT a.id, a.username FROM auth_tokens t JOIN accounts a ON a.id = t.account_id
            WHERE t.token_hash = ? AND t.expires_at > ?
            """.trimIndent(),
            tokenHash,
            now,
        ) { AccountPrincipal(it.getLong("id"), it.getString("username")) }
    }

    suspend fun deleteExpired(now: Instant) {
        database.transaction { connection ->
            connection.update("DELETE FROM auth_challenges WHERE expires_at <= ?", now)
            connection.update("DELETE FROM auth_tokens WHERE expires_at <= ?", now)
        }
    }
}
