package app.blesk.server.spaces

import app.blesk.server.db.Database
import app.blesk.server.db.query
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant

class Space(val id: Long, val handle: String, val kind: SpaceKind, val title: String, val about: String)

enum class ChannelRemoval { Removed, NotFound, Last }

/** Database codes match the wire names, so the column reads the same as the JSON. */
internal fun SpaceKind.code() = name.lowercase()

internal fun SpaceRole.code() = name.lowercase()

internal fun spaceKind(code: String) = SpaceKind.entries.first { it.code() == code }

internal fun spaceRole(code: String) = SpaceRole.entries.first { it.code() == code }

class SpaceRepository(private val database: Database) {
    /** False when the handle is taken. The creator becomes the owner. */
    suspend fun create(
        handle: String,
        kind: SpaceKind,
        title: String,
        about: String,
        ownerId: Long,
        firstChannel: SpaceChannel,
        now: Instant,
    ): Boolean = database.transaction { connection ->
        val spaceId = connection.queryOne(
            """
            INSERT INTO spaces (handle, kind, title, about, created_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (handle) DO NOTHING
            RETURNING id
            """.trimIndent(),
            handle,
            kind.code(),
            title,
            about,
            now,
        ) { it.getLong("id") } ?: return@transaction false
        connection.update(
            "INSERT INTO space_channels (space_id, id, title) VALUES (?, ?, ?)",
            spaceId,
            firstChannel.id,
            firstChannel.title,
        )
        connection.update(
            "INSERT INTO space_members (space_id, account_id, role, joined_at) VALUES (?, ?, ?, ?)",
            spaceId,
            ownerId,
            SpaceRole.Owner.code(),
            now,
        )
        true
    }

    suspend fun find(handle: String): Space? = database.transaction { connection ->
        connection.queryOne("SELECT id, handle, kind, title, about FROM spaces WHERE handle = ?", handle, map = ::space)
    }

    suspend fun info(space: Space, callerId: Long): SpaceInfo = database.transaction { it.info(space, callerId) }

    /** Handle prefix or title substring, case-insensitive; the biggest spaces first. */
    suspend fun search(text: String, callerId: Long, limit: Int): List<SpaceInfo> = database.transaction { connection ->
        val pattern = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        connection.query(
            """
            SELECT s.id, s.handle, s.kind, s.title, s.about FROM spaces s
            WHERE s.handle LIKE ? OR lower(s.title) LIKE ?
            ORDER BY (SELECT count(*) FROM space_members m WHERE m.space_id = s.id) DESC, s.handle
            LIMIT ?
            """.trimIndent(),
            "$pattern%",
            "%$pattern%",
            limit,
            map = ::space,
        ).map { connection.info(it, callerId) }
    }

    suspend fun joined(accountId: Long): List<SpaceInfo> = database.transaction { connection ->
        connection.query(
            """
            SELECT s.id, s.handle, s.kind, s.title, s.about FROM spaces s
            JOIN space_members m ON m.space_id = s.id
            WHERE m.account_id = ?
            ORDER BY m.joined_at DESC
            """.trimIndent(),
            accountId,
            map = ::space,
        ).map { connection.info(it, accountId) }
    }

    suspend fun update(spaceId: Long, title: String?, about: String?) {
        database.transaction { connection ->
            connection.update(
                "UPDATE spaces SET title = coalesce(?, title), about = coalesce(?, about) WHERE id = ?",
                title,
                about,
                spaceId,
            )
        }
    }

    suspend fun delete(spaceId: Long) {
        database.transaction { it.update("DELETE FROM spaces WHERE id = ?", spaceId) }
    }

    suspend fun role(spaceId: Long, accountId: Long): SpaceRole? = database.transaction { it.role(spaceId, accountId) }

    /** False when the account is banned from the space. Joining twice changes nothing. */
    suspend fun join(spaceId: Long, accountId: Long, now: Instant): Boolean = database.transaction { connection ->
        val banned = connection.queryOne("SELECT 1 FROM space_bans WHERE space_id = ? AND account_id = ?", spaceId, accountId) { true }
        if (banned == true) return@transaction false
        connection.update(
            """
            INSERT INTO space_members (space_id, account_id, role, joined_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (space_id, account_id) DO NOTHING
            """.trimIndent(),
            spaceId,
            accountId,
            SpaceRole.Member.code(),
            now,
        )
        true
    }

    suspend fun leave(spaceId: Long, accountId: Long) {
        database.transaction { it.update("DELETE FROM space_members WHERE space_id = ? AND account_id = ?", spaceId, accountId) }
    }

    suspend fun members(spaceId: Long, offset: Int, limit: Int): List<SpaceMember> = database.transaction { connection ->
        connection.query(
            """
            SELECT a.username, m.role FROM space_members m JOIN accounts a ON a.id = m.account_id
            WHERE m.space_id = ?
            ORDER BY CASE m.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, a.username
            OFFSET ? LIMIT ?
            """.trimIndent(),
            spaceId,
            offset,
            limit,
        ) { SpaceMember(it.getString("username"), spaceRole(it.getString("role"))) }
    }

    suspend fun memberIds(spaceId: Long): List<Long> = database.transaction { connection ->
        connection.query("SELECT account_id FROM space_members WHERE space_id = ?", spaceId) { it.getLong("account_id") }
    }

    suspend fun setRole(spaceId: Long, accountId: Long, role: SpaceRole) {
        database.transaction { connection ->
            connection.update("UPDATE space_members SET role = ? WHERE space_id = ? AND account_id = ?", role.code(), spaceId, accountId)
        }
    }

    suspend fun ban(spaceId: Long, accountId: Long) {
        database.transaction { connection ->
            connection.update("DELETE FROM space_members WHERE space_id = ? AND account_id = ?", spaceId, accountId)
            connection.update("INSERT INTO space_bans (space_id, account_id) VALUES (?, ?) ON CONFLICT DO NOTHING", spaceId, accountId)
        }
    }

    suspend fun unban(spaceId: Long, accountId: Long) {
        database.transaction { it.update("DELETE FROM space_bans WHERE space_id = ? AND account_id = ?", spaceId, accountId) }
    }

    suspend fun channelExists(spaceId: Long, channelId: String): Boolean = database.transaction { connection ->
        connection.queryOne("SELECT 1 FROM space_channels WHERE space_id = ? AND id = ?", spaceId, channelId) { true } ?: false
    }

    /** False when the space already has [max] channels. */
    suspend fun addChannel(spaceId: Long, channel: SpaceChannel, max: Int): Boolean = database.transaction { connection ->
        connection.lockSpace(spaceId)
        if (connection.channels(spaceId).size >= max) return@transaction false
        connection.update("INSERT INTO space_channels (space_id, id, title) VALUES (?, ?, ?)", spaceId, channel.id, channel.title)
        true
    }

    /** Removing a channel removes its posts too. A space always keeps at least one channel. */
    suspend fun removeChannel(spaceId: Long, channelId: String): ChannelRemoval = database.transaction { connection ->
        connection.lockSpace(spaceId)
        val channels = connection.channels(spaceId)
        when {
            channels.none { it.id == channelId } -> ChannelRemoval.NotFound
            channels.size == 1 -> ChannelRemoval.Last
            else -> {
                connection.update("DELETE FROM space_channels WHERE space_id = ? AND id = ?", spaceId, channelId)
                ChannelRemoval.Removed
            }
        }
    }

    /** Serializes channel changes of one space, so two concurrent adds cannot both pass the limit. */
    private fun Connection.lockSpace(spaceId: Long) {
        queryOne("SELECT id FROM spaces WHERE id = ? FOR UPDATE", spaceId) { it.getLong("id") }
    }

    private fun Connection.role(spaceId: Long, accountId: Long): SpaceRole? =
        queryOne("SELECT role FROM space_members WHERE space_id = ? AND account_id = ?", spaceId, accountId) {
            spaceRole(it.getString("role"))
        }

    private fun Connection.channels(spaceId: Long): List<SpaceChannel> =
        query("SELECT id, title FROM space_channels WHERE space_id = ? ORDER BY position", spaceId) {
            SpaceChannel(it.getString("id"), it.getString("title"))
        }

    private fun Connection.info(space: Space, callerId: Long): SpaceInfo {
        val members = queryOne("SELECT count(*) AS n FROM space_members WHERE space_id = ?", space.id) { it.getInt("n") } ?: 0
        return SpaceInfo(
            handle = space.handle,
            kind = space.kind,
            title = space.title,
            about = space.about,
            members = members,
            channels = channels(space.id),
            role = role(space.id, callerId),
        )
    }

    private fun space(row: ResultSet) = Space(
        id = row.getLong("id"),
        handle = row.getString("handle"),
        kind = spaceKind(row.getString("kind")),
        title = row.getString("title"),
        about = row.getString("about"),
    )
}
