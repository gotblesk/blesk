package app.blesk.server.invites

import app.blesk.server.db.Database
import app.blesk.server.db.instant
import app.blesk.server.db.instantOrNull
import app.blesk.server.db.query
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList
import app.blesk.shared.protocol.Invites
import java.security.SecureRandom
import java.sql.Connection
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

private val Lifetime = Invites.TTL_DAYS.days.toJavaDuration()
private val KeptAfterUse = Invites.KEEP_DAYS.days.toJavaDuration()
private const val BOOTSTRAP_CREATED = "bootstrap_invites_created"

/**
 * Invites of people, and bootstrap ones: those have no owner, never expire and are made on the
 * server itself with `blesk-server invites`, so that its owner can register first.
 */
class InviteRepository(private val database: Database, private val random: SecureRandom = SecureRandom()) {
    /** The person's invites that still work and the used ones still remembered, newest first. */
    suspend fun list(ownerId: Long, now: Instant): InviteList = database.transaction { connection ->
        val invites = connection.query(
            """
            SELECT i.code, i.created_at, i.expires_at, i.used_at, a.username
            FROM invites i LEFT JOIN accounts a ON a.id = i.used_by
            WHERE i.owner_id = ? AND ((i.used_at IS NULL AND i.expires_at > ?) OR i.used_at > ?)
            ORDER BY i.created_at DESC, i.code
            """.trimIndent(),
            ownerId,
            now,
            now - KeptAfterUse,
        ) { row ->
            InviteInfo(
                code = row.getString("code"),
                createdAtMillis = row.instant("created_at").toEpochMilli(),
                expiresAtMillis = row.instant("expires_at").toEpochMilli(),
                usedBy = row.getString("username"),
                usedAtMillis = row.instantOrNull("used_at")?.toEpochMilli(),
            )
        }
        InviteList(invites, left = (Invites.LIMIT - invites.count { it.usedAtMillis == null }).coerceAtLeast(0))
    }

    /** A new invite of the person's, or null while [Invites.LIMIT] of theirs still wait to be used. */
    suspend fun create(ownerId: Long, now: Instant): InviteInfo? = database.transaction { connection ->
        // Two requests at once must not both take the last free place.
        connection.queryOne("SELECT id FROM accounts WHERE id = ? FOR NO KEY UPDATE", ownerId) {}
        val waiting = connection.queryOne(
            "SELECT count(*) FROM invites WHERE owner_id = ? AND used_at IS NULL AND expires_at > ?",
            ownerId,
            now,
        ) { it.getInt(1) } ?: 0
        if (waiting >= Invites.LIMIT) return@transaction null
        val expiresAt = now + Lifetime
        InviteInfo(connection.insert(ownerId, now, expiresAt), now.toEpochMilli(), expiresAt.toEpochMilli())
    }

    /** Makes [count] bootstrap invites unless some were ever made before; false if they were. */
    suspend fun createFirstBootstrap(count: Int, now: Instant): Boolean = database.transaction { connection ->
        val first = connection.markBootstrapCreated(now)
        if (first) repeat(count) { connection.insert(ownerId = null, now, expiresAt = null) }
        first
    }

    /** Makes [count] more bootstrap invites and returns their codes. */
    suspend fun createBootstrap(count: Int, now: Instant): List<String> = database.transaction { connection ->
        connection.markBootstrapCreated(now)
        List(count) { connection.insert(ownerId = null, now, expiresAt = null) }
    }

    /** Codes of the bootstrap invites nobody has used yet, oldest first. */
    suspend fun unusedBootstrap(): List<String> = database.transaction { connection ->
        connection.query("SELECT code FROM invites WHERE owner_id IS NULL AND used_at IS NULL ORDER BY created_at, code") {
            it.getString("code")
        }
    }

    /** Forgets who came by an invite [Invites.KEEP_DAYS] after it was used, and drops expired unused ones. */
    suspend fun deleteStale(now: Instant) {
        database.transaction { connection ->
            connection.update("DELETE FROM invites WHERE used_at <= ?", now - KeptAfterUse)
            connection.update("DELETE FROM invites WHERE used_at IS NULL AND expires_at <= ?", now)
        }
    }

    /** About 59 random bits: a clash is next to impossible, and when it happens the next code is tried. */
    private fun Connection.insert(ownerId: Long?, now: Instant, expiresAt: Instant?): String =
        generateSequence(::newCode).first { code ->
            update(
                "INSERT INTO invites (code, owner_id, created_at, expires_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                code,
                ownerId,
                now,
                expiresAt,
            ) == 1
        }

    private fun newCode(): String = String(CharArray(Invites.CODE_LENGTH) { Invites.ALPHABET[random.nextInt(Invites.ALPHABET.length)] })

    /** True the first time only. */
    private fun Connection.markBootstrapCreated(now: Instant): Boolean =
        update("INSERT INTO server_events (name, happened_at) VALUES (?, ?) ON CONFLICT DO NOTHING", BOOTSTRAP_CREATED, now) == 1
}

/**
 * Part of a registration's transaction: locks [code] if it still works. A second registration with
 * the same code waits for the first one and then finds the invite used.
 */
fun Connection.lockUsableInvite(code: String, now: Instant): Boolean = queryOne(
    "SELECT code FROM invites WHERE code = ? AND used_at IS NULL AND (expires_at IS NULL OR expires_at > ?) FOR UPDATE",
    code,
    now,
) { true } ?: false

/** Uses up an invite locked by [lockUsableInvite] in the same transaction. */
fun Connection.redeemInvite(code: String, accountId: Long, now: Instant) {
    update("UPDATE invites SET used_by = ?, used_at = ? WHERE code = ?", accountId, now, code)
}
