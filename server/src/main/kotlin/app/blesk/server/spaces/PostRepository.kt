package app.blesk.server.spaces

import app.blesk.server.BleskJson
import app.blesk.server.db.Database
import app.blesk.server.db.instant
import app.blesk.server.db.query
import app.blesk.server.db.queryOne
import app.blesk.server.db.update
import app.blesk.shared.protocol.AttachmentPointer
import app.blesk.shared.protocol.Post
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime

class StoredPost(val authorId: Long, val post: Post)

class PostDraft(
    val spaceId: Long,
    val channel: String,
    val authorId: Long,
    val clientId: String,
    val text: String,
    val replyTo: Long?,
    val attachment: AttachmentPointer?,
)

sealed interface PostCreation {
    val post: Post

    data class Created(override val post: Post) : PostCreation

    /** The author already sent this client id; the earlier post is returned unchanged. */
    data class Duplicate(override val post: Post) : PostCreation
}

class PostRepository(private val database: Database) {
    suspend fun create(draft: PostDraft, now: Instant): PostCreation = database.transaction { connection ->
        val id = connection.queryOne(
            """
            INSERT INTO space_posts
                (space_id, channel_id, author_id, client_id, text, reply_to, attachment, attachment_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (author_id, client_id) DO NOTHING
            RETURNING id
            """.trimIndent(),
            draft.spaceId,
            draft.channel,
            draft.authorId,
            draft.clientId,
            draft.text,
            draft.replyTo,
            draft.attachment?.let { BleskJson.encodeToString(AttachmentPointer.serializer(), it) },
            draft.attachment?.id,
            now,
        ) { it.getLong("id") }
        if (id != null) {
            PostCreation.Created(checkNotNull(connection.post("p.id = ?", id)).post)
        } else {
            val previous = connection.post("p.author_id = ? AND p.client_id = ?", draft.authorId, draft.clientId)
            PostCreation.Duplicate(checkNotNull(previous).post)
        }
    }

    suspend fun find(spaceId: Long, postId: Long): StoredPost? = database.transaction { connection ->
        connection.post("p.id = ? AND p.space_id = ?", postId, spaceId)
    }

    /** Newest first, strictly older than [before] when given. */
    suspend fun page(spaceId: Long, channel: String, before: Long?, limit: Int): List<Post> = database.transaction { connection ->
        connection.query(
            "$SELECT WHERE p.space_id = ? AND p.channel_id = ? AND p.id < ? ORDER BY p.id DESC LIMIT ?",
            spaceId,
            channel,
            before ?: Long.MAX_VALUE,
            limit,
            map = ::stored,
        ).map { it.post }
    }

    suspend fun edit(postId: Long, text: String, now: Instant): Post = database.transaction { connection ->
        connection.update("UPDATE space_posts SET text = ?, edited_at = ? WHERE id = ?", text, now, postId)
        checkNotNull(connection.post("p.id = ?", postId)).post
    }

    suspend fun delete(postId: Long) {
        database.transaction { it.update("DELETE FROM space_posts WHERE id = ?", postId) }
    }

    private fun Connection.post(condition: String, vararg params: Any?): StoredPost? =
        queryOne("$SELECT WHERE $condition", *params, map = ::stored)

    private fun stored(row: ResultSet) = StoredPost(
        authorId = row.getLong("author_id"),
        post = Post(
            id = row.getLong("id"),
            space = row.getString("handle"),
            channel = row.getString("channel_id"),
            author = row.getString("username"),
            text = row.getString("text"),
            createdAtMillis = row.instant("created_at").toEpochMilli(),
            replyTo = row.getLong("reply_to").takeUnless { row.wasNull() },
            attachment = row.getString("attachment")?.let { BleskJson.decodeFromString(AttachmentPointer.serializer(), it) },
            editedAtMillis = row.getObject("edited_at", OffsetDateTime::class.java)?.toInstant()?.toEpochMilli(),
        ),
    )

    private companion object {
        const val SELECT = """
            SELECT p.id, s.handle, p.channel_id, p.author_id, a.username, p.text, p.created_at, p.reply_to, p.attachment, p.edited_at
            FROM space_posts p JOIN spaces s ON s.id = p.space_id JOIN accounts a ON a.id = p.author_id
        """
    }
}
