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
    /** JSON of the link card. */
    val link: String? = null,
    /** JSON of where the files of a received message lie on the server, kept until they are downloaded. */
    val remote: String? = null,
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
    /** A contact: you opened the chat or wrote to them. Someone who only wrote to you is not one yet. */
    val added: Boolean = true,
    /** Nothing from them reaches you, and they cannot tell. */
    val blocked: Boolean = false,
    /** Their profile photo in the media folder, if they let you see it. */
    val photo: String? = null,
    /** Their profile links, one per line. */
    val links: String = "",
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

/** A closed group or room: its state as members agreed on it. */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    /** JSON of the shared GroupState. */
    val state: String,
)

/**
 * Our current sender key for one audience of a group: [scope] "" for everyone in it, a channel's id for the readers of
 * that closed channel. It stays in use only while everyone in [holders] may still read and the group is at [epoch].
 */
@Entity(tableName = "group_sender_keys", primaryKeys = ["groupId", "scope"])
data class GroupSenderKeyEntity(
    val groupId: String,
    val scope: String,
    /** The key's libsignal distribution id, random and never used for another key. */
    val distributionId: String,
    val epoch: Int,
    /** JSON list of members it was handed to. */
    val holders: String = "[]",
)

/**
 * A group message this phone may not read yet, from a sender whose state of the group is newer: kept until that state
 * arrives, for a while at most, and never shown, indexed or announced meanwhile. A state of the group refused while
 * ours was behind waits here too, as a group update, to be tried again.
 */
@Entity(tableName = "held_group_messages", indices = [Index(value = ["groupId"])])
data class HeldGroupMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val sender: String,
    /** The decrypted Content JSON; like the outbox, it never leaves the encrypted database. */
    val content: String,
    /** The version of the group's state the sender wrote it in. */
    val version: Long,
    val heldAt: Long,
)

/**
 * The current media key and LiveKit room of a closed voice channel, as this phone has it: made here if it is the
 * room's keeper, its first admin, or given by an admin otherwise.
 */
@Entity(tableName = "voice_keys", primaryKeys = ["groupId", "channelId"])
data class VoiceKeyEntity(
    val groupId: String,
    val channelId: String,
    /** Grows with every new key of the channel; a phone keeps the highest, or the keeper's latest over another admin's. */
    val version: Long,
    val room: String,
    /** Base64 media key. */
    val key: String,
    /** For a key made here, the group's epoch it was made in: a key from another epoch is not handed out again. */
    val epoch: Int,
    /** JSON list of members a key made here was handed to; null for a key given by another admin. */
    val holders: String? = null,
    /** The admin who gave it; null for a key made here. */
    val giver: String? = null,
)

/**
 * A group this phone let go of: left, dissolved, or removed from. Its row and chats are gone; this keeps an update
 * that was already on its way from bringing them back.
 */
@Entity(tableName = "left_groups")
data class LeftGroupEntity(
    @PrimaryKey val id: String,
    /** The version it was let go at; only a state made after it, a newer one, may bring the group back. */
    val version: Long,
    /** Left or dissolved by us: no admin's update brings it back, only a request of ours to join. */
    val byUs: Boolean,
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
