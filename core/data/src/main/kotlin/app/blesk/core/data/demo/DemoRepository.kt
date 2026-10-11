package app.blesk.core.data.demo

import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Preview
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.SendStatus
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The offline world of demo mode. Everything lives in memory and is rebuilt on start, so the
 * demo never writes anything that could be mistaken for real correspondence.
 */
class DemoRepository(
    seed: DemoSeed,
    /** Short preview of a message that has only an attachment, e.g. "Голосовое". */
    private val attachmentLabels: (Attachment?) -> String,
    private val scope: CoroutineScope,
    private val log: EventLog,
    private val clock: () -> Instant = Instant::now,
) {
    private val contactsById: Map<String, Contact> = seed.contacts.associateBy { it.id }
    private val messages = MutableStateFlow(seed.messages.groupBy { it.conversationId })
    private val all = MutableStateFlow(seed.conversations)
    private val typingState = MutableStateFlow<Set<String>>(emptySet())
    private val ids = AtomicLong()
    private val echoBotId = seed.echoBotConversationId
    private val botAuthorId = seed.echoBotAuthorId

    /** People you can write to directly. */
    val contacts: List<Contact> = seed.contacts.filter { it.id in seed.directContactIds }

    /** Conversations shown in the list; room channels live inside their rooms, empty direct chats stay hidden. */
    val conversations: Flow<List<Conversation>> = all.map { list ->
        list.filter { it.parentId == null && (it.kind != ConversationKind.Direct || it.preview != null) }
    }

    /** Conversations where someone is typing right now. */
    val typing: StateFlow<Set<String>> = typingState.asStateFlow()

    fun conversation(id: String): Flow<Conversation?> = all.map { list -> list.firstOrNull { it.id == id } }

    fun messages(conversationId: String): Flow<List<Message>> = messages.map { it[conversationId].orEmpty() }

    fun contact(id: String): Contact? = contactsById[id]

    /** The direct chat with [contactId], started empty if there was none. */
    fun directChatWith(contactId: String): String {
        val contact = contactsById.getValue(contactId)
        if (all.value.none { it.id == contactId }) {
            all.update { it + Conversation(contactId, ConversationKind.Direct, contact.name, Protection.EndToEnd, null, clock()) }
        }
        return contactId
    }

    fun send(conversationId: String, text: String, replyToId: String? = null) {
        val body = text.trim()
        if (body.isEmpty()) return
        val message = Message(
            id = nextId(),
            conversationId = conversationId,
            authorId = ME,
            text = body,
            sentAt = clock(),
            status = SendStatus.Sending,
            replyToId = replyToId,
        )
        append(message)
        log.record(EventCode.DemoMessageSent)
        scope.launch {
            for (status in listOf(SendStatus.Sent, SendStatus.Delivered, SendStatus.Read)) {
                delay(STATUS_STEP_MILLIS)
                update(message.conversationId, message.id) { it.copy(status = status) }
            }
            if (conversationId == echoBotId) echo(message)
        }
    }

    /** Shares a theme into the chat as a message anyone there can apply. */
    fun sendTheme(conversationId: String, theme: ThemeSpec, caption: String) {
        sendAttachment(conversationId, Attachment.Theme(theme.normalized()), caption)
    }

    /** Sends a photo, video, file, voice note or circle; the echo bot sends it back. */
    fun sendAttachment(conversationId: String, attachment: Attachment, caption: String = "", replyToId: String? = null) {
        val message = Message(
            id = nextId(),
            conversationId = conversationId,
            authorId = ME,
            text = caption.trim(),
            sentAt = clock(),
            status = SendStatus.Sending,
            replyToId = replyToId,
            attachment = attachment,
        )
        append(message)
        log.record(EventCode.DemoMessageSent)
        scope.launch {
            for (status in listOf(SendStatus.Sent, SendStatus.Delivered, SendStatus.Read)) {
                delay(STATUS_STEP_MILLIS)
                update(message.conversationId, message.id) { it.copy(status = status) }
            }
            if (conversationId == echoBotId) echo(message)
        }
    }

    private suspend fun echo(message: Message) {
        typingState.update { it + message.conversationId }
        delay(TYPING_MILLIS)
        typingState.update { it - message.conversationId }
        append(message.copy(id = nextId(), authorId = botAuthorId, sentAt = clock(), replyToId = null, status = SendStatus.Read))
        log.record(EventCode.DemoBotReplied)
    }

    fun toggleReaction(conversationId: String, messageId: String, symbol: String) {
        update(conversationId, messageId) { message ->
            val existing = message.reactions.firstOrNull { it.symbol == symbol }
            val reactions = when {
                existing == null -> message.reactions + Reaction(symbol, 1, mine = true)
                existing.mine && existing.count == 1 -> message.reactions - existing
                existing.mine -> message.reactions.map { if (it == existing) it.copy(count = it.count - 1, mine = false) else it }
                else -> message.reactions.map { if (it == existing) it.copy(count = it.count + 1, mine = true) else it }
            }
            message.copy(reactions = reactions.toImmutableList())
        }
    }

    fun edit(conversationId: String, messageId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        update(conversationId, messageId) { if (it.isOwn) it.copy(text = body, edited = true) else it }
        refreshPreview(conversationId)
    }

    /** Deletes for everyone. Only your own messages can be deleted. */
    fun delete(conversationId: String, messageId: String) {
        messages.update { map ->
            val list = map[conversationId].orEmpty().filterNot { it.id == messageId && it.isOwn }
            map + (conversationId to list)
        }
        refreshPreview(conversationId)
    }

    fun markRead(conversationId: String) {
        all.update { list -> list.map { if (it.id == conversationId) it.copy(unread = 0) else it } }
    }

    private fun nextId() = "local-${ids.incrementAndGet()}"

    private fun update(conversationId: String, messageId: String, transform: (Message) -> Message) {
        messages.update { map ->
            val list = map[conversationId].orEmpty().map { if (it.id == messageId) transform(it) else it }
            map + (conversationId to list)
        }
    }

    private fun append(message: Message) {
        messages.update { map -> map + (message.conversationId to (map[message.conversationId].orEmpty() + message)) }
        refreshPreview(message.conversationId)
    }

    private fun refreshPreview(conversationId: String) {
        val conversation = all.value.firstOrNull { it.id == conversationId } ?: return
        val last = messages.value[conversationId]?.lastOrNull()
        val targetId = conversation.parentId ?: conversationId
        val showAuthor = conversation.parentId != null || conversation.memberCount > 2
        all.update { list ->
            list.map {
                if (it.id != targetId || last == null) return@map it
                val author = if (last.isOwn || !showAuthor) null else contactsById[last.authorId]?.name?.substringBefore(' ')
                val text = last.text.ifEmpty { attachmentLabels(last.attachment) }
                it.copy(preview = Preview(author, text), updatedAt = maxOf(it.updatedAt, last.sentAt))
            }.sortedWith(conversationOrder)
        }
    }

    companion object {
        private const val STATUS_STEP_MILLIS = 450L
        private const val TYPING_MILLIS = 1400L

        val conversationOrder: Comparator<Conversation> =
            compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt }
    }
}
