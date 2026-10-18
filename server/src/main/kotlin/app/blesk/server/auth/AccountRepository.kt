package app.blesk.server.auth

import app.blesk.server.db.Database
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.server.invites.lockUsableInvite
import app.blesk.server.invites.redeemInvite
import app.blesk.shared.protocol.Api
import java.time.Instant

class Account(
    val id: Long,
    val username: String,
    val identityKey: ByteArray,
    val registrationId: Int,
    val discoverable: Boolean = true,
) {
    /** Whether a lookup may see this account: by its name when it is discoverable, otherwise only with its link key. */
    fun visibleTo(viewer: Long, link: String?): Boolean = discoverable || viewer == id || link == Api.linkKey(identityKey)
}

/** How a registration ended. */
enum class Registration { Created, UsernameTaken, InviteInvalid }

class AccountRepository(private val database: Database) {
    /**
     * Creates the account. With an [invite], only if that invite still works; it is used up in the
     * same transaction, so a refused name leaves it as it was.
     */
    suspend fun create(
        username: String,
        identityKey: ByteArray,
        registrationId: Int,
        now: Instant,
        recoveryHash: ByteArray? = null,
        invite: String? = null,
    ): Registration = database.transaction { connection ->
        if (invite != null && !connection.lockUsableInvite(invite, now)) return@transaction Registration.InviteInvalid
        val id = connection.queryOne(
            """
            INSERT INTO accounts (username, identity_key, registration_id, created_at, recovery_hash)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING id
            """.trimIndent(),
            username,
            identityKey,
            registrationId,
            now,
            recoveryHash,
        ) { it.getLong("id") } ?: return@transaction Registration.UsernameTaken
        if (invite != null) connection.redeemInvite(invite, id, now)
        Registration.Created
    }

    suspend fun find(username: String): Account? = database.transaction { connection ->
        connection.queryOne(
            "SELECT id, username, identity_key, registration_id, discoverable FROM accounts WHERE username = ?",
            username,
        ) { row ->
            Account(
                row.getLong("id"),
                row.getString("username"),
                row.getBytes("identity_key"),
                row.getInt("registration_id"),
                row.getBoolean("discoverable"),
            )
        }
    }

    /** False when another account already has this hash, which for 256-bit tokens means a mistake, not a clash. */
    suspend fun setRecovery(id: Long, hash: ByteArray): Boolean = database.transaction { connection ->
        connection.queryOne("SELECT id FROM accounts WHERE recovery_hash = ? AND id <> ?", hash, id) { true } == null &&
            connection.update("UPDATE accounts SET recovery_hash = ? WHERE id = ?", hash, id) == 1
    }

    /**
     * Moves the account found by [hash] to a new phone's identity key. Everything bound to the old
     * phone goes: its prekeys, its sessions and the messages waiting for it, which it alone could read.
     */
    suspend fun recover(hash: ByteArray, identityKey: ByteArray, registrationId: Int): Account? =
        database.transaction { connection ->
            val id = connection.queryOne("SELECT id FROM accounts WHERE recovery_hash = ?", hash) { it.getLong("id") }
                ?: return@transaction null
            connection.update("UPDATE accounts SET identity_key = ?, registration_id = ? WHERE id = ?", identityKey, registrationId, id)
            listOf("signed_prekeys", "kyber_last_resort_keys", "one_time_prekeys", "kyber_prekeys", "auth_tokens").forEach { table ->
                connection.update("DELETE FROM $table WHERE account_id = ?", id)
            }
            connection.update("DELETE FROM queued_messages WHERE recipient_id = ?", id)
            connection.queryOne("SELECT username, discoverable FROM accounts WHERE id = ?", id) { row ->
                Account(id, row.getString("username"), identityKey, registrationId, row.getBoolean("discoverable"))
            }
        }

    /** Removes the account; its keys, tokens and waiting messages go with it through the foreign keys. */
    suspend fun delete(id: Long) {
        database.transaction { connection -> connection.update("DELETE FROM accounts WHERE id = ?", id) }
    }

    suspend fun setDiscoverable(id: Long, discoverable: Boolean) {
        database.transaction { connection ->
            connection.update("UPDATE accounts SET discoverable = ? WHERE id = ?", discoverable, id)
        }
    }
}
