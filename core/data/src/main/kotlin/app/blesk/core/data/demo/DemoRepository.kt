package app.blesk.core.data.demo

import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Preview
import app.blesk.core.data.model.SendStatus
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
    private val scope: CoroutineScope,
    private val log: EventLog,
    private val clock: () -> Instant = Instant::now,
) {
    private val contacts: Map<String, Contact> = seed.contacts.associateBy { it.id }
    private val messages = MutableStateFlow(seed.messages.groupBy { it.conversationId })
    private val conversationsState = MutableStateFlow(seed.conversations)
    private val ids = AtomicLong()
    private val echoBotId = seed.echoBotConversationId
    private val botAuthorId = seed.echoBotAuthorId

    val conversations: StateFlow<List<Conversation>> = conversationsState.asStateFlow()

    fun conversation(id: String): Flow<Conversation?> = conversationsState.map { list -> list.firstOrNull { it.id == id } }

    fun messages(conversationId: String): Flow<List<Message>> = messages.map { it[conversationId].orEmpty() }

    fun contact(id: String): Contact? = contacts[id]

    fun send(conversationId: String, text: String, replyToId: String? = null) {
        val body = text.trim()
        if (body.isEmpty()) return
        val message = Message(
            id = "local-${ids.incrementAndGet()}",
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
                setStatus(message, status)
            }
            if (conversationId == echoBotId) {
                delay(STATUS_STEP_MILLIS)
                append(message.copy(id = "local-${ids.incrementAndGet()}", authorId = botAuthorId, sentAt = clock(), replyToId = null))
                log.record(EventCode.DemoBotReplied)
            }
        }
    }

    fun markRead(conversationId: String) {
        conversationsState.update { list -> list.map { if (it.id == conversationId) it.copy(unread = 0) else it } }
    }

    private fun append(message: Message) {
        messages.update { all -> all + (message.conversationId to (all[message.conversationId].orEmpty() + message)) }
        val author = if (message.isOwn) null else contacts[message.authorId]?.name
        conversationsState.update { list ->
            list.map {
                if (it.id == message.conversationId) {
                    it.copy(preview = Preview(author, message.text), updatedAt = message.sentAt)
                } else {
                    it
                }
            }.sortedWith(conversationOrder)
        }
    }

    private fun setStatus(message: Message, status: SendStatus) {
        messages.update { all ->
            val list = all[message.conversationId].orEmpty().map { if (it.id == message.id) it.copy(status = status) else it }
            all + (message.conversationId to list)
        }
    }

    companion object {
        private const val STATUS_STEP_MILLIS = 450L

        val conversationOrder: Comparator<Conversation> =
            compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt }
    }
}
