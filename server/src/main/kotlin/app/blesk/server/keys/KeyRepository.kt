package app.blesk.server.keys

import app.blesk.server.db.Database
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.shared.protocol.KeyStatus
import java.sql.Connection

class StoredKey(val id: Int, val publicKey: ByteArray, val signature: ByteArray?)

class ValidatedUpload(
    val signedPreKey: StoredKey?,
    val kyberLastResort: StoredKey?,
    val preKeys: List<StoredKey>,
    val kyberPreKeys: List<StoredKey>,
)

class Bundle(val signedPreKey: StoredKey, val preKey: StoredKey?, val kyberPreKey: StoredKey, val remaining: KeyStatus)

class KeyRepository(private val database: Database) {
    /** Applies the upload, or nothing at all when it would leave more than [cap] one-time keys of either kind. */
    suspend fun upload(accountId: Long, upload: ValidatedUpload, cap: Int): Boolean = database.transaction { connection ->
        upload.signedPreKey?.let { connection.replaceSigned("signed_prekeys", accountId, it) }
        upload.kyberLastResort?.let { connection.replaceSigned("kyber_last_resort_keys", accountId, it) }
        upload.preKeys.forEach { key ->
            connection.update(
                """
                INSERT INTO one_time_prekeys (account_id, key_id, public_key) VALUES (?, ?, ?)
                ON CONFLICT (account_id, key_id) DO UPDATE SET public_key = EXCLUDED.public_key
                """.trimIndent(),
                accountId,
                key.id,
                key.publicKey,
            )
        }
        upload.kyberPreKeys.forEach { key ->
            connection.update(
                """
                INSERT INTO kyber_prekeys (account_id, key_id, public_key, signature) VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id, key_id) DO UPDATE SET public_key = EXCLUDED.public_key, signature = EXCLUDED.signature
                """.trimIndent(),
                accountId,
                key.id,
                key.publicKey,
                key.signature,
            )
        }
        val status = connection.status(accountId)
        if (status.preKeys > cap || status.kyberPreKeys > cap) {
            connection.rollback()
            false
        } else {
            true
        }
    }

    suspend fun status(accountId: Long): KeyStatus = database.transaction { it.status(accountId) }

    /**
     * Hands out one EC and one Kyber one-time key, deleting them in the same transaction. SKIP LOCKED
     * lets two senders fetching at once each get a different key instead of waiting on each other.
     */
    /** The keys to start a session with [accountId]; without [oneTimeKeys] only the signed and last-resort ones. */
    suspend fun takeBundle(accountId: Long, oneTimeKeys: Boolean = true): Bundle? = database.transaction { connection ->
        val signed = connection.signed("signed_prekeys", accountId) ?: return@transaction null
        if (!oneTimeKeys) {
            val lastResort = connection.signed("kyber_last_resort_keys", accountId) ?: return@transaction null
            return@transaction Bundle(signed, null, lastResort, connection.status(accountId))
        }
        val preKey = connection.queryOne(
            """
            DELETE FROM one_time_prekeys WHERE (account_id, key_id) = (
                SELECT account_id, key_id FROM one_time_prekeys WHERE account_id = ? ORDER BY key_id LIMIT 1 FOR UPDATE SKIP LOCKED
            ) RETURNING key_id, public_key
            """.trimIndent(),
            accountId,
        ) { StoredKey(it.getInt("key_id"), it.getBytes("public_key"), null) }
        val kyberPreKey = connection.queryOne(
            """
            DELETE FROM kyber_prekeys WHERE (account_id, key_id) = (
                SELECT account_id, key_id FROM kyber_prekeys WHERE account_id = ? ORDER BY key_id LIMIT 1 FOR UPDATE SKIP LOCKED
            ) RETURNING key_id, public_key, signature
            """.trimIndent(),
            accountId,
        ) { StoredKey(it.getInt("key_id"), it.getBytes("public_key"), it.getBytes("signature")) }
            ?: connection.signed("kyber_last_resort_keys", accountId)
            ?: return@transaction null
        Bundle(signed, preKey, kyberPreKey, connection.status(accountId))
    }

    private fun Connection.replaceSigned(table: String, accountId: Long, key: StoredKey) {
        update(
            """
            INSERT INTO $table (account_id, key_id, public_key, signature) VALUES (?, ?, ?, ?)
            ON CONFLICT (account_id) DO UPDATE
            SET key_id = EXCLUDED.key_id, public_key = EXCLUDED.public_key, signature = EXCLUDED.signature
            """.trimIndent(),
            accountId,
            key.id,
            key.publicKey,
            key.signature,
        )
    }

    private fun Connection.signed(table: String, accountId: Long): StoredKey? =
        queryOne("SELECT key_id, public_key, signature FROM $table WHERE account_id = ?", accountId) {
            StoredKey(it.getInt("key_id"), it.getBytes("public_key"), it.getBytes("signature"))
        }

    private fun Connection.status(accountId: Long): KeyStatus = checkNotNull(
        queryOne(
            """
            SELECT (SELECT count(*) FROM one_time_prekeys WHERE account_id = ?) AS ec,
                   (SELECT count(*) FROM kyber_prekeys WHERE account_id = ?) AS kyber
            """.trimIndent(),
            accountId,
            accountId,
        ) { KeyStatus(it.getInt("ec"), it.getInt("kyber")) },
    )
}
