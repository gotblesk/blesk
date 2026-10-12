package app.blesk.core.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val protection: String,
    /** The other person's username for direct chats. */
    val peer: String?,
    val updatedAt: Long,
    val unread: Int = 0,
    val pinned: Boolean = false,
    /** Place among pinned chats, smallest first; meaningless when not pinned. */
    val pinRank: Int = 0,
    val previewAuthor: String? = null,
    val previewText: String? = null,
    /** For a room channel: the room's conversation id. */
    val parentId: String? = null,
    val draft: String = "",
    val timerSeconds: Int = 0,
    /** Pinned message ids, oldest first, joined by [PIN_SEPARATOR]; the column predates several pins. */
    val pinnedMessageId: String? = null,
    val archived: Boolean = false,
    /** Epoch millis until which the chat is silent; [Long.MAX_VALUE] is forever, 0 is not muted. */
    val mutedUntil: Long = 0,
    val markedUnread: Boolean = false,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "sentAt"])],
)
data class MessageEntity(
    /** The sender-chosen content id; the same on both sides. */
    @PrimaryKey val id: String,
    val conversationId: String,
    val authorId: String,
    val text: String,
    val sentAt: Long,
    val status: String,
    val replyToId: String? = null,
    /** JSON list of reactions. */
    val reactions: String = "[]",
    val edited: Boolean = false,
    /** JSON of the attachment as stored locally. */
    val attachment: String? = null,
    /** JSON rows of inline buttons under a bot message. */
    val buttons: String? = null,
    /** Epoch millis when it disappears; null keeps it. */
    val expiresAt: Long? = null,
    val forwardedFrom: String? = null,
)

/** Full-text index over message text, kept in step with [MessageEntity] by Room's triggers. */
@Fts4(contentEntity = MessageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(val text: String)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val username: String,
    val name: String,
    val bio: String = "",
    val identityKey: String? = null,
    val verified: Boolean = false,
    /** Their key changed since you last looked; the chat warns until you check it. */
    val keyChanged: Boolean = false,
)

/** Encrypted-later work: content waiting to be encrypted and sent. Survives restarts. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipient: String,
    /** The plaintext Content JSON; it never leaves the encrypted database unencrypted. */
    val content: String,
    val ephemeral: Boolean = false,
    val attempts: Int = 0,
    val createdAt: Long,
    /** Group messages are encrypted once with the sender key and fanned out as is. */
    val sealedType: Int? = null,
    val sealedBody: String? = null,
)

/** A closed group or room: its state as members agreed on it, and who already has our sender key. */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    /** JSON of the shared GroupState. */
    val state: String,
    /** JSON list of members who received our sender key for [keyEpoch]. */
    val keyHolders: String = "[]",
    val keyEpoch: Int = -1,
)

/** Key-value storage for libsignal state: one table, namespaced keys, opaque records. */
@Entity(tableName = "signal_state", primaryKeys = ["kind", "name"])
data class SignalStateEntity(
    val kind: String,
    val name: String,
    val record: ByteArray,
    val extra: Long = 0,
) {
    override fun equals(other: Any?) = other is SignalStateEntity && kind == other.kind && name == other.name &&
        record.contentEquals(other.record) && extra == other.extra

    override fun hashCode() = (kind.hashCode() * 31 + name.hashCode()) * 31 + record.contentHashCode()
}

/** Message ids are UUIDs or server ids, neither of which contains a comma. */
const val PIN_SEPARATOR = ","

fun ConversationEntity.pinnedIds(): List<String> = pinnedMessageId?.split(PIN_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
