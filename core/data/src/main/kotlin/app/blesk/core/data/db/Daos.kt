package app.blesk.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // one query per column the list can change
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, CASE WHEN pinned = 1 THEN pinRank ELSE 0 END ASC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT id FROM conversations WHERE kind = 'Direct' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentDirect(limit: Int): List<String>

    @Query("SELECT * FROM conversations WHERE parentId = :parentId")
    suspend fun children(parentId: String): List<ConversationEntity>

    @Query("DELETE FROM conversations WHERE id = :id OR parentId = :id")
    suspend fun deleteWithChildren(id: String)

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET draft = :draft WHERE id = :id")
    suspend fun setDraft(id: String, draft: String)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE conversations SET mutedUntil = :until WHERE id = :id")
    suspend fun setMuted(id: String, until: Long)

    @Query("UPDATE conversations SET markedUnread = :unread WHERE id = :id")
    suspend fun setMarkedUnread(id: String, unread: Boolean)

    @Query("UPDATE conversations SET pinned = :pinned, pinRank = :rank WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, rank: Int)

    @Query("SELECT COALESCE(MAX(pinRank), -1) FROM conversations WHERE pinned = 1")
    suspend fun lastPinRank(): Int

    @Query("UPDATE conversations SET pinRank = :rank WHERE id = :id")
    suspend fun setPinRank(id: String, rank: Int)

    @Transaction
    suspend fun reorderPinned(ids: List<String>) {
        ids.forEachIndexed { rank, id -> setPinRank(id, rank) }
    }

    /** A new message brings a chat with sound back from the archive; a muted one stays there. */
    @Query("UPDATE conversations SET archived = 0 WHERE id = :id AND archived = 1 AND mutedUntil <= :now")
    suspend fun returnFromArchive(id: String, now: Long)

    @Query("UPDATE conversations SET pinnedMessageId = :messageId WHERE id = :id")
    suspend fun pinMessage(id: String, messageId: String?)

    @Query("UPDATE conversations SET timerSeconds = :seconds WHERE id = :id")
    suspend fun setTimer(id: String, seconds: Int)

    @Query("UPDATE conversations SET unread = 0, markedUnread = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE conversations SET unread = unread + 1 WHERE id = :id")
    suspend fun incrementUnread(id: String)

    @Query("UPDATE conversations SET previewAuthor = :author, previewText = :text, updatedAt = MAX(updatedAt, :at) WHERE id = :id")
    suspend fun setPreview(id: String, author: String?, text: String?, at: Long)
}

@Dao
@Suppress("TooManyFunctions")
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

    /** Disappearing messages whose time is up; returns their conversations to refresh previews. */
    @Query("SELECT DISTINCT conversationId FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun expiredConversations(now: Long): List<String>

    @Query("SELECT * FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun expiredMessages(now: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT MIN(expiresAt) FROM messages WHERE expiresAt IS NOT NULL")
    suspend fun nextExpiry(): Long?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE attachment IS NOT NULL")
    suspend fun withFiles(): List<MessageEntity>

    /** Every message of a chat, so its files can be removed before the rows. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND attachment IS NOT NULL")
    suspend fun withFilesIn(conversationId: String): List<MessageEntity>

    /** Full-text search; [match] is an FTS query such as `"свет"*`. Case and Cyrillic are handled by unicode61. */
    @Query(
        "SELECT messages.* FROM messages JOIN messages_fts ON messages.rowid = messages_fts.rowid " +
            "WHERE messages_fts MATCH :match ORDER BY messages.sentAt DESC LIMIT 200",
    )
    suspend fun searchFullText(match: String): List<MessageEntity>

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

    @Query("SELECT * FROM contacts WHERE username = :username")
    fun observe(username: String): Flow<ContactEntity?>

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET added = 1 WHERE username = :username")
    suspend fun markAdded(username: String)

    @Query("UPDATE contacts SET blocked = :blocked WHERE username = :username")
    suspend fun setBlocked(username: String, blocked: Boolean)
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

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun get(id: String): GroupEntity?

    @Query("SELECT * FROM groups")
    fun observeAll(): Flow<List<GroupEntity>>

    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)
}
