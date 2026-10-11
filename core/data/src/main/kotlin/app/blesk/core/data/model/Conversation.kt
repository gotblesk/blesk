package app.blesk.core.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import java.time.Instant

/** How a conversation is protected. Drives the glint: only [EndToEnd] shines. */
enum class Protection { EndToEnd, Open }

enum class ConversationKind { Direct, Group, Room, Channel, Bot }

data class Contact(val id: String, val name: String, val username: String)

data class Preview(val authorName: String?, val text: String)

data class Conversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val protection: Protection,
    val preview: Preview?,
    val updatedAt: Instant,
    val unread: Int = 0,
    val pinned: Boolean = false,
    val memberCount: Int = 0,
    val channels: ImmutableList<RoomChannel> = persistentListOf(),
)

enum class RoomChannelKind { Text, Voice }

data class RoomChannel(
    val id: String,
    val title: String,
    val kind: RoomChannelKind,
    val unread: Int = 0,
    val participants: ImmutableList<Contact> = persistentListOf(),
    val speaking: ImmutableSet<String> = persistentSetOf(),
)

enum class SendStatus { Sending, Sent, Delivered, Read }

data class Message(
    val id: String,
    val conversationId: String,
    val authorId: String,
    val text: String,
    val sentAt: Instant,
    val status: SendStatus = SendStatus.Read,
    val replyToId: String? = null,
) {
    val isOwn: Boolean get() = authorId == ME
}

const val ME = "me"
