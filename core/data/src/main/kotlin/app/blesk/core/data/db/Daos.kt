package app.blesk.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.blesk.core.data.model.SendStatus
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

    /** Creates the row if there is none; an existing one keeps its preview, unread count, draft and the rest. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String)

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

    /** Messages that may carry a song; the attachment is decoded to be sure. */
    @Query("SELECT * FROM messages WHERE attachment LIKE '%music%' ORDER BY sentAt DESC")
    fun observeMusic(): Flow<List<MessageEntity>>

    /** Every message of a chat, so its files can be removed before the rows. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND attachment IS NOT NULL")
    suspend fun withFilesIn(conversationId: String): List<MessageEntity>

    /**
     * The room's channels, `room:channel`, that have messages on this phone. Every id that starts with `room:` sorts
     * between it and `room;`, so the chat index answers this alone, without reading other rooms or any message.
     */
    @Query("SELECT DISTINCT conversationId FROM messages WHERE conversationId >= :roomId || ':' AND conversationId < :roomId || ';'")
    suspend fun channelsWithMessages(roomId: String): List<String>

    /** The latest message in any of [conversationIds]. */
    @Query("SELECT * FROM messages WHERE conversationId IN (:conversationIds) ORDER BY sentAt DESC LIMIT 1")
    suspend fun lastIn(conversationIds: List<String>): MessageEntity?

    /** Full-text search; [match] is an FTS query such as `"свет"*`. Case and Cyrillic are handled by unicode61. */
    @Query(
        "SELECT messages.* FROM messages JOIN messages_fts ON messages.rowid = messages_fts.rowid " +
            "WHERE messages_fts MATCH :match ORDER BY messages.sentAt DESC LIMIT 200",
    )
    suspend fun searchFullText(match: String): List<MessageEntity>

    /**
     * Moves the status of [author]'s messages forward and never back, in one statement: a receipt and the
     * sending coroutine may arrive in any order, and whichever comes second must not undo the first.
     */
    suspend fun advanceStatus(ids: List<String>, author: String, status: SendStatus) {
        val below = SendStatus.entries.filter { it < status }.map { it.name }
        ids.chunked(STATUS_BATCH).forEach { raiseStatus(it, author, status.name, below) }
    }

    @Query("UPDATE messages SET status = :status WHERE id IN (:ids) AND authorId = :author AND status IN (:below)")
    suspend fun raiseStatus(ids: List<String>, author: String, status: String, below: List<String>)

    /** Sets the status whatever it was; only for an explicit failure. */
    @Query("UPDATE messages SET status = :status WHERE id IN (:ids) AND authorId = :author")
    suspend fun setStatus(ids: List<String>, author: String, status: String)

    // One column each: none of these can put back a status, a text or reactions read before someone else wrote them.

    @Query("UPDATE messages SET text = :text, edited = 1 WHERE id = :id AND authorId = :author")
    suspend fun editText(id: String, author: String, text: String): Int

    /** A public post the server sent again, edited or with its chat now known; its file and the rest stay. */
    @Query(
        "UPDATE messages SET conversationId = :conversationId, text = :text, edited = :edited, replyToId = :replyToId " +
            "WHERE id = :id",
    )
    suspend fun setPost(id: String, conversationId: String, text: String, edited: Boolean, replyToId: String?)

    @Query("UPDATE messages SET buttons = :buttons WHERE id = :id")
    suspend fun setButtons(id: String, buttons: String?)

    @Query("UPDATE messages SET attachment = :attachment WHERE id = :id")
    suspend fun setAttachment(id: String, attachment: String?)

    @Query("UPDATE messages SET link = :link WHERE id = :id")
    suspend fun setLink(id: String, link: String?)

    @Query("UPDATE messages SET remote = NULL WHERE id = :id")
    suspend fun clearRemote(id: String)

    @Query("SELECT reactions FROM messages WHERE id = :id")
    suspend fun reactions(id: String): String?

    @Query("UPDATE messages SET reactions = :reactions WHERE id = :id")
    suspend fun setReactions(id: String, reactions: String)

    /** Reads and rewrites the reactions in one transaction, so yours and theirs arriving together are both kept. */
    @Transaction
    suspend fun changeReactions(id: String, change: (String) -> String) {
        val current = reactions(id) ?: return
        setReactions(id, change(current))
    }

    @Query("SELECT id FROM messages WHERE conversationId = :conversationId AND authorId != :me AND status != 'Read'")
    suspend fun unreadIncoming(conversationId: String, me: String): List<String>

    @Query("UPDATE messages SET status = 'Read' WHERE id IN (:ids)")
    suspend fun markIncomingRead(ids: List<String>)
}

/** Well under SQLite's limit on bound values; a receipt may name any number of messages. */
private const val STATUS_BATCH = 500

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE username = :username")
    suspend fun get(username: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE username = :username")
    fun observe(username: String): Flow<ContactEntity?>

    /**
     * Creates the row if there is none. Every column of an existing contact has its own writer below
     * (you add, block or verify; their profile names them), and none of them rewrites the others.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(contact: ContactEntity): Long

    @Query("UPDATE contacts SET added = 1 WHERE username = :username")
    suspend fun markAdded(username: String)

    /** Adds them, keeping the identity key already on file. */
    @Query("UPDATE contacts SET added = 1, identityKey = COALESCE(identityKey, :identityKey) WHERE username = :username")
    suspend fun markAddedWithKey(username: String, identityKey: String?)

    @Query("UPDATE contacts SET name = :name, bio = :bio, links = :links, photo = :photo WHERE username = :username")
    suspend fun setProfile(username: String, name: String, bio: String, links: String, photo: String?)

    @Query("UPDATE contacts SET name = :name WHERE username = :username")
    suspend fun setName(username: String, name: String)

    /** Checking the key clears the warning about a changed one; taking the mark off leaves the warning as it is. */
    @Query(
        "UPDATE contacts SET verified = :verified, keyChanged = CASE WHEN :verified THEN 0 ELSE keyChanged END " +
            "WHERE username = :username",
    )
    suspend fun setVerified(username: String, verified: Boolean)

    @Query("UPDATE contacts SET verified = 0, keyChanged = 1 WHERE username = :username")
    suspend fun markKeyChanged(username: String): Int

    @Query("UPDATE contacts SET added = 0 WHERE username = :username")
    suspend fun markRemoved(username: String)

    @Query("SELECT * FROM contacts")
    suspend fun all(): List<ContactEntity>

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

/** How many messages a group may have waiting for its state; the oldest go first. */
const val MAX_HELD_PER_GROUP = 200

/**
 * A held message a state at `:ownerVersion` and `:version` may judge: one written in that state or an older one, as
 * `GroupPolicy.newer` orders them, or, from an app too old to send the owner's count, at no later version.
 */
private const val HELD_UP_TO = "(ownerVersion < :ownerVersion OR (ownerVersion = :ownerVersion AND version <= :version) " +
    "OR (ownerVersion IS NULL AND version <= :version))"

/**
 * How many refused states of a group this phone keeps from one sender, and from all: each is judged again whenever
 * another state is taken, so their number bounds that work, and one sender's flood does not push out the others'.
 */
const val MAX_REFUSED_PER_SENDER = 4
const val MAX_REFUSED_PER_GROUP = 32

@Dao
@Suppress("TooManyFunctions") // the group, our sender keys and what waits for its state live together
interface GroupDao {
    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun get(id: String): GroupEntity?

    @Query("SELECT * FROM groups")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT id FROM groups")
    suspend fun ids(): List<String>

    /** Creates the row of a group new to this phone; the state of an existing one has its own writer below. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(group: GroupEntity): Long

    /** Returns 0 for a group no longer on this phone: one left or dissolved meanwhile stays deleted. */
    @Query("UPDATE groups SET state = :state WHERE id = :id")
    suspend fun setState(id: String, state: String): Int

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM group_sender_keys WHERE groupId = :groupId AND scope = :scope")
    suspend fun senderKey(groupId: String, scope: String): GroupSenderKeyEntity?

    @Upsert
    suspend fun upsertSenderKey(key: GroupSenderKeyEntity)

    /** Writes only while the group is on this phone: a send that finishes after we left leaves no key of it behind. */
    @Transaction
    suspend fun putSenderKey(key: GroupSenderKeyEntity) {
        if (get(key.groupId) != null) upsertSenderKey(key)
    }

    @Query("DELETE FROM group_sender_keys WHERE groupId = :groupId")
    suspend fun deleteSenderKeys(groupId: String)

    @Insert
    suspend fun insertHeld(message: HeldGroupMessageEntity): Long

    @Query(
        "DELETE FROM held_group_messages WHERE groupId = :groupId AND id NOT IN " +
            "(SELECT id FROM held_group_messages WHERE groupId = :groupId ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun trimHeld(groupId: String, keep: Int)

    /** Keeps [message] until its state arrives, and only the newest [MAX_HELD_PER_GROUP] of its group. */
    @Transaction
    suspend fun hold(message: HeldGroupMessageEntity) {
        insertHeld(message)
        trimHeld(message.groupId, MAX_HELD_PER_GROUP)
    }

    @Query("SELECT * FROM held_group_messages WHERE groupId = :groupId AND $HELD_UP_TO ORDER BY id ASC")
    suspend fun heldUpTo(groupId: String, ownerVersion: Long, version: Long): List<HeldGroupMessageEntity>

    @Query("DELETE FROM held_group_messages WHERE groupId = :groupId AND $HELD_UP_TO")
    suspend fun deleteHeldUpTo(groupId: String, ownerVersion: Long, version: Long)

    /** Takes the held messages a state at [ownerVersion] and [version] may judge, in the order they came, off the list. */
    @Transaction
    suspend fun releasable(groupId: String, ownerVersion: Long, version: Long): List<HeldGroupMessageEntity> =
        heldUpTo(groupId, ownerVersion, version).also { deleteHeldUpTo(groupId, ownerVersion, version) }

    @Query("DELETE FROM held_group_messages WHERE groupId = :groupId")
    suspend fun deleteHeld(groupId: String)

    @Query("DELETE FROM held_group_messages WHERE heldAt < :at")
    suspend fun dropHeldBefore(at: Long)

    @Insert
    suspend fun insertRefused(state: RefusedGroupStateEntity): Long

    @Query(
        "DELETE FROM refused_group_states WHERE groupId = :groupId AND sender = :sender AND id NOT IN " +
            "(SELECT id FROM refused_group_states WHERE groupId = :groupId AND sender = :sender ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun trimRefusedFrom(groupId: String, sender: String, keep: Int)

    @Query(
        "DELETE FROM refused_group_states WHERE groupId = :groupId AND id NOT IN " +
            "(SELECT id FROM refused_group_states WHERE groupId = :groupId ORDER BY managing DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimRefused(groupId: String, keep: Int)

    /**
     * Keeps [state], and only the newest [MAX_REFUSED_PER_SENDER] of its sender and [MAX_REFUSED_PER_GROUP] of its group,
     * those of senders who manage it before the rest: members who manage nothing push out only each other's.
     */
    @Transaction
    suspend fun keepRefused(state: RefusedGroupStateEntity) {
        insertRefused(state)
        trimRefusedFrom(state.groupId, state.sender, MAX_REFUSED_PER_SENDER)
        trimRefused(state.groupId, MAX_REFUSED_PER_GROUP)
    }

    /** The kept states of [groupId] newer than one at [ownerVersion] and [version], oldest first. */
    @Query(
        "SELECT * FROM refused_group_states WHERE groupId = :groupId AND " +
            "(ownerVersion > :ownerVersion OR (ownerVersion = :ownerVersion AND version > :version)) " +
            "ORDER BY ownerVersion ASC, version ASC, id ASC",
    )
    suspend fun refusedAfter(groupId: String, ownerVersion: Long, version: Long): List<RefusedGroupStateEntity>

    /** Lets go of the kept states of [groupId] no newer than one at [ownerVersion] and [version]: none will be taken. */
    @Query(
        "DELETE FROM refused_group_states WHERE groupId = :groupId AND " +
            "(ownerVersion < :ownerVersion OR (ownerVersion = :ownerVersion AND version <= :version))",
    )
    suspend fun dropRefusedUpTo(groupId: String, ownerVersion: Long, version: Long)

    @Query("DELETE FROM refused_group_states WHERE id = :id")
    suspend fun unkeepRefused(id: Long)

    @Query("DELETE FROM refused_group_states WHERE groupId = :groupId")
    suspend fun deleteRefused(groupId: String)

    @Query("DELETE FROM refused_group_states WHERE keptAt < :at")
    suspend fun dropRefusedBefore(at: Long)

    @Query("SELECT * FROM voice_keys WHERE groupId = :groupId AND channelId = :channelId")
    suspend fun voiceKey(groupId: String, channelId: String): VoiceKeyEntity?

    /** For the keeper's own key, written in the transaction that read its group. */
    @Upsert
    suspend fun upsertVoiceKey(key: VoiceKeyEntity)

    /**
     * Keeps a key given by an admin, only while its group is on this phone: if it is newer than the one here, or comes
     * from the room's [keeper] in place of one that did not. A version is only what its giver says: an admin could give
     * one no later key is newer than, and it must not outlast the keeper's next key. Returns whether it was kept.
     */
    @Transaction
    suspend fun offerVoiceKey(key: VoiceKeyEntity, keeper: String?): Boolean {
        if (get(key.groupId) == null) return false
        val current = voiceKey(key.groupId, key.channelId)
        val replacesAnother = key.giver != null && key.giver == keeper && current?.giver != keeper
        if (current != null && current.version >= key.version && !replacesAnother) return false
        upsertVoiceKey(key)
        return true
    }

    @Query("DELETE FROM voice_keys WHERE groupId = :groupId")
    suspend fun deleteVoiceKeys(groupId: String)

    @Query("SELECT * FROM left_groups WHERE id = :id")
    suspend fun leftGroup(id: String): LeftGroupEntity?

    @Query("SELECT id FROM left_groups")
    suspend fun leftIds(): List<String>

    @Upsert
    suspend fun markLeft(group: LeftGroupEntity)

    @Query("DELETE FROM left_groups WHERE id = :id")
    suspend fun unmarkLeft(id: String)
}
