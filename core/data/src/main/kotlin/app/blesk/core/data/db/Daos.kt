package app.blesk.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET unread = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE conversations SET unread = unread + 1 WHERE id = :id")
    suspend fun incrementUnread(id: String)

    @Query("UPDATE conversations SET previewAuthor = :author, previewText = :text, updatedAt = MAX(updatedAt, :at) WHERE id = :id")
    suspend fun setPreview(id: String, author: String?, text: String?, at: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt DESC LIMIT 1")
    suspend fun last(conversationId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE messages SET status = :status WHERE id IN (:ids) AND authorId = :author")
    suspend fun setStatus(ids: List<String>, author: String, status: String)

    @Query("SELECT id FROM messages WHERE conversationId = :conversationId AND authorId != :me AND status != 'Read'")
    suspend fun unreadIncoming(conversationId: String, me: String): List<String>

    @Query("UPDATE messages SET status = 'Read' WHERE id IN (:ids)")
    suspend fun markIncomingRead(ids: List<String>)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE username = :username")
    suspend fun get(username: String): ContactEntity?

    @Upsert
    suspend fun upsert(contact: ContactEntity)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun all(): List<OutboxEntity>

    @Insert
    suspend fun insert(item: OutboxEntity): Long

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)
}

/** Blocking on purpose: libsignal calls its stores synchronously from its own thread. */
@Dao
interface SignalStateDao {
    @Query("SELECT * FROM signal_state WHERE kind = :kind AND name = :name")
    fun get(kind: String, name: String): SignalStateEntity?

    @Query("SELECT * FROM signal_state WHERE kind = :kind")
    fun all(kind: String): List<SignalStateEntity>

    @Query("SELECT COUNT(*) FROM signal_state WHERE kind = :kind")
    fun count(kind: String): Int

    @Upsert
    fun put(entity: SignalStateEntity)

    @Query("DELETE FROM signal_state WHERE kind = :kind AND name = :name")
    fun delete(kind: String, name: String)

    @Query("DELETE FROM signal_state WHERE kind = :kind AND name LIKE :prefix || '%'")
    fun deleteByPrefix(kind: String, prefix: String)
}
