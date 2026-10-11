package app.blesk.core.messaging

import app.blesk.core.data.db.ContactEntity
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Preview
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.model.SendStatus
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

internal val storageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/** Who reacted with what, as stored; turned into counts for the screen. */
@Serializable
internal data class ReactionEntry(val symbol: String, val users: List<String>)

internal fun decodeReactions(raw: String): List<ReactionEntry> =
    runCatching { storageJson.decodeFromString(ListSerializer(ReactionEntry.serializer()), raw) }.getOrDefault(emptyList())

internal fun encodeReactions(entries: List<ReactionEntry>): String =
    storageJson.encodeToString(ListSerializer(ReactionEntry.serializer()), entries)

internal fun decodeAttachment(raw: String?): Attachment? =
    raw?.let { runCatching { storageJson.decodeFromString(Attachment.serializer(), it) }.getOrNull() }

internal fun encodeAttachment(attachment: Attachment?): String? =
    attachment?.let { storageJson.encodeToString(Attachment.serializer(), it) }

internal fun MessageEntity.toModel(me: String) = Message(
    id = id,
    conversationId = conversationId,
    authorId = if (authorId == me) ME else authorId,
    text = text,
    sentAt = Instant.ofEpochMilli(sentAt),
    status = runCatching { SendStatus.valueOf(status) }.getOrDefault(SendStatus.Sent),
    replyToId = replyToId,
    reactions = decodeReactions(reactions).map { Reaction(it.symbol, it.users.size, me in it.users) }.toImmutableList(),
    edited = edited,
    attachment = decodeAttachment(attachment),
    buttons = decodeButtons(buttons),
)

private val buttonRows = ListSerializer(ListSerializer(BotButton.serializer()))

internal fun decodeButtons(json: String?): ImmutableList<ImmutableList<BotButton>> =
    json?.let { runCatching { storageJson.decodeFromString(buttonRows, it) }.getOrNull() }
        ?.map { it.toImmutableList() }?.toImmutableList()
        ?: persistentListOf()

/** A room carries its channels from the group state, so the list can show it as a cluster. */
internal fun ConversationEntity.toModel(state: GroupState? = null) = Conversation(
    id = id,
    kind = runCatching { ConversationKind.valueOf(kind) }.getOrDefault(ConversationKind.Direct),
    title = title,
    protection = runCatching { Protection.valueOf(protection) }.getOrDefault(Protection.EndToEnd),
    preview = previewText?.let { Preview(previewAuthor, it) },
    updatedAt = Instant.ofEpochMilli(updatedAt),
    unread = unread,
    pinned = pinned,
    memberCount = state?.members?.size ?: 0,
    channels = state?.channels?.map { channel ->
        RoomChannel(
            id = if (channel.kind == GroupChannelKind.Text) "${id}${GroupEngine.CHANNEL_SEPARATOR}${channel.id}" else channel.id,
            title = channel.title,
            kind = if (channel.kind == GroupChannelKind.Voice) RoomChannelKind.Voice else RoomChannelKind.Text,
        )
    }.orEmpty().toImmutableList(),
    parentId = parentId,
)

internal fun ContactEntity.toModel() = Contact(id = username, name = name.ifBlank { username }, username = username)

/** A public space as a conversation: its channels (for a room) come from the server's description. */
internal fun ConversationEntity.toModel(space: SpaceInfo) = toModel().copy(
    memberCount = space.members,
    channels = if (space.kind == SpaceKind.Room) {
        space.channels.map { RoomChannel(SpaceEngine.conversation(space.handle, it.id), it.title, RoomChannelKind.Text) }.toImmutableList()
    } else {
        persistentListOf()
    },
)
