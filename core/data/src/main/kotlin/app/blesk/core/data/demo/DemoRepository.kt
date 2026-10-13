package app.blesk.core.data.demo

import app.blesk.core.data.BotAccounts
import app.blesk.core.data.BotController
import app.blesk.core.data.BotResult
import app.blesk.core.data.BotWithToken
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Presence
import app.blesk.core.data.model.Preview
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.model.splitLongText
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The offline world of demo mode. Everything lives in memory and is rebuilt on start, so the
 * demo never writes anything that could be mistaken for real correspondence.
 */
class DemoRepository private constructor(
    seed: DemoSeed,
    /** Short preview of a message that has only an attachment, e.g. "Голосовое". */
    private val attachmentLabels: (Attachment?) -> String,
    private val scope: CoroutineScope,
    private val log: EventLog,
    private val clock: () -> Instant,
    world: DemoWorld,
    communities: DemoCommunities,
) : ChatRepository, BotController, BotAccounts, GroupController by communities, SpaceController by communities {
    constructor(
        seed: DemoSeed,
        attachmentLabels: (Attachment?) -> String,
        scope: CoroutineScope,
        log: EventLog,
        clock: () -> Instant = Instant::now,
    ) : this(seed, attachmentLabels, scope, log, clock, DemoWorld(seed))

    private constructor(
        seed: DemoSeed,
        attachmentLabels: (Attachment?) -> String,
        scope: CoroutineScope,
        log: EventLog,
        clock: () -> Instant,
        world: DemoWorld,
    ) : this(seed, attachmentLabels, scope, log, clock, world, DemoCommunities(seed, world, seed.contacts, seed.community, scope, clock))

    private val contactsById: Map<String, Contact> = seed.contacts.associateBy { it.id }
    private val messages = world.messages
    private val all = world.conversations
    private val typingState = MutableStateFlow<Set<String>>(emptySet())
    private val ids = AtomicLong()
    private val echoBotId = seed.echoBotConversationId
    private val botAuthorId = seed.echoBotAuthorId
    private val bot = seed.bot

    private val blockedIds = MutableStateFlow<Set<String>>(emptySet())
    private val people = seed.contacts

    private val removedIds = MutableStateFlow(emptySet<String>())

    override val contacts: Flow<List<Contact>> = combine(blockedIds, removedIds) { blocked, removed ->
        seed.contacts.filter { it.id in seed.directContactIds && it.id !in blocked && it.id !in removed }
    }

    override fun removeContact(username: String) {
        val id = people.firstOrNull { it.username == username }?.id ?: username
        removedIds.update { it + id }
        change(id) { it.copy(request = true) }
    }

    override val blocked: Flow<List<Contact>> = blockedIds.map { blocked -> people.filter { it.id in blocked } }

    override fun accept(username: String) {
        val id = people.firstOrNull { it.username == username }?.id ?: username
        removedIds.update { it - id }
        change(id) { it.copy(request = false) }
    }

    override fun setBlocked(username: String, blocked: Boolean) {
        val id = people.firstOrNull { it.username == username }?.id ?: username
        blockedIds.update { if (blocked) it + id else it - id }
    }

    /** Empty direct chats stay hidden until something is written. */
    override val conversations: Flow<List<Conversation>> = combine(all, blockedIds) { list, blocked ->
        list.filter { it.parentId == null && (it.kind != ConversationKind.Direct || it.preview != null) && it.id !in blocked }
    }

    override val typing: StateFlow<Set<String>> = typingState.asStateFlow()

    // Аня is around and Миша was here a couple of hours ago; refreshed so Аня never goes stale.
    override val presence: StateFlow<Map<String, Presence>> = flow {
        while (true) {
            val now = clock()
            emit(
                mapOf(
                    DemoSeed.ANYA to Presence(online = true, lastSeen = null, heardAt = now),
                    DemoSeed.MISHA to Presence(online = false, lastSeen = now.minus(Duration.ofHours(2)), heardAt = now),
                ),
            )
            delay(PRESENCE_REFRESH_MILLIS)
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun conversation(id: String): Flow<Conversation?> = all.map { list -> list.firstOrNull { it.id == id } }

    override fun messages(conversationId: String): Flow<List<Message>> = messages.map { it[conversationId].orEmpty() }

    override val music: Flow<List<Message>> =
        messages.map { all -> all.values.flatten().filter { it.attachment is Attachment.Music }.sortedByDescending { it.sentAt } }

    /** Group members are named by username, chat authors by id; the demo people answer to both. */
    override fun contact(id: String): Contact? = contactsById[id] ?: people.firstOrNull { it.username == id }

    override suspend fun openDirect(username: String, link: String?): String? {
        val contactId = contactsById.values.firstOrNull { it.username == username || it.id == username }?.id ?: return null
        val contact = contactsById.getValue(contactId)
        if (all.value.none { it.id == contactId }) {
            all.update { it + Conversation(contactId, ConversationKind.Direct, contact.name, Protection.EndToEnd, null, clock()) }
        }
        return contactId
    }

    override fun send(conversationId: String, text: String, replyToId: String?) {
        splitLongText(text).forEachIndexed { index, part -> sendPart(conversationId, part, replyToId.takeIf { index == 0 }) }
    }

    private fun sendPart(conversationId: String, body: String, replyToId: String?) {
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

    /** The echo bot sends attachments back too. */
    override fun sendAttachment(conversationId: String, attachment: Attachment, caption: String, replyToId: String?) {
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
        val start = message.text.trim() == "/start" || message.text.trim() == "/help"
        val reply = if (start) {
            message.copy(
                text = bot.greeting,
                buttons = persistentListOf(bot.buttons.map { BotButton(it, data = it) }.toImmutableList()),
            )
        } else {
            message
        }
        append(reply.copy(id = nextId(), authorId = botAuthorId, sentAt = clock(), replyToId = null, status = SendStatus.Read))
        log.record(EventCode.DemoBotReplied)
    }

    // The demo bot

    private val noticeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val notices: SharedFlow<String> = noticeFlow

    override fun isBot(conversationId: String) = conversationId == echoBotId

    override suspend fun commands(conversationId: String): List<BotCommand> = if (isBot(conversationId)) bot.commands else emptyList()

    override fun press(conversationId: String, messageId: String, data: String) {
        if (!isBot(conversationId)) return
        scope.launch {
            delay(STATUS_STEP_MILLIS)
            append(
                Message(
                    id = nextId(),
                    conversationId = conversationId,
                    authorId = botAuthorId,
                    text = bot.pressed.format(data),
                    sentAt = clock(),
                ),
            )
        }
    }

    override suspend fun myBots(): List<BotInfo>? = emptyList()

    override suspend fun createBot(username: String, name: String, about: String): Pair<BotResult, BotWithToken?> = BotResult.Failed to null

    override suspend fun renewToken(username: String): BotWithToken? = null

    override suspend fun deleteBot(username: String) = false

    override suspend fun search(query: String): List<Message> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return messages.value.values.flatten()
            .filter { it.text.contains(needle, ignoreCase = true) }
            .sortedByDescending { it.sentAt }
            .take(SEARCH_LIMIT)
    }

    override fun toggleReaction(conversationId: String, messageId: String, symbol: String) {
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

    override fun edit(conversationId: String, messageId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        update(conversationId, messageId) { if (it.isOwn) it.copy(text = body, edited = true) else it }
        refreshPreview(conversationId)
    }

    override fun delete(conversationId: String, messageId: String, forEveryone: Boolean) {
        messages.update { map ->
            val list = map[conversationId].orEmpty().filterNot { it.id == messageId && (it.isOwn || !forEveryone) }
            map + (conversationId to list)
        }
        refreshPreview(conversationId)
    }

    override fun saveDraft(conversationId: String, text: String) = change(conversationId) { it.copy(draft = text) }

    override fun setChatPinned(conversationId: String, pinned: Boolean) {
        val rank = if (pinned) (all.value.filter { it.pinned }.maxOfOrNull { it.pinRank } ?: -1) + 1 else 0
        change(conversationId) { it.copy(pinned = pinned, pinRank = rank) }
        all.update { it.sortedWith(conversationOrder) }
    }

    override fun reorderPinned(ids: List<String>) {
        all.update { list ->
            list.map { conversation ->
                val rank = ids.indexOf(conversation.id)
                if (rank >= 0) conversation.copy(pinRank = rank) else conversation
            }.sortedWith(conversationOrder)
        }
    }

    override fun setArchived(conversationId: String, archived: Boolean) = change(conversationId) { it.copy(archived = archived) }

    override fun deleteChat(conversationId: String, forBoth: Boolean) {
        all.update { list -> list.filterNot { it.id == conversationId } }
        messages.update { it - conversationId }
    }

    override fun mute(conversationId: String, untilMillis: Long) = change(conversationId) { it.copy(mutedUntil = untilMillis) }

    override fun markUnread(conversationId: String, unread: Boolean) = change(conversationId) { it.copy(markedUnread = unread) }

    override fun pinMessage(conversationId: String, messageId: String, pinned: Boolean) = change(conversationId) {
        val rest = it.pinnedMessageIds - messageId
        it.copy(pinnedMessageIds = (if (pinned) rest + messageId else rest).toImmutableList())
    }

    override fun setTimer(conversationId: String, seconds: Int) = change(conversationId) { it.copy(timerSeconds = seconds) }

    override fun forward(conversationId: String, messageId: String, to: List<String>) {
        val original = messages.value[conversationId]?.firstOrNull { it.id == messageId } ?: return
        to.forEach { target ->
            append(
                original.copy(
                    id = nextId(),
                    conversationId = target,
                    authorId = ME,
                    sentAt = clock(),
                    status = SendStatus.Read,
                    replyToId = null,
                    reactions = persistentListOf(),
                    forwardedFrom = FORWARDED,
                    buttons = persistentListOf(),
                ),
            )
        }
    }

    private fun change(conversationId: String, transform: (Conversation) -> Conversation) {
        all.update { list -> list.map { if (it.id == conversationId) transform(it) else it } }
    }

    override fun markRead(conversationId: String) {
        all.update { list -> list.map { if (it.id == conversationId) it.copy(unread = 0, markedUnread = false) else it } }
    }

    private fun nextId() = "local-${ids.incrementAndGet()}"

    private fun update(conversationId: String, messageId: String, transform: (Message) -> Message) {
        messages.update { map ->
            val list = map[conversationId].orEmpty().map { if (it.id == messageId) transform(it) else it }
            map + (conversationId to list)
        }
    }

    private fun append(message: Message) {
        val timer = all.value.firstOrNull { it.id == message.conversationId }?.timerSeconds ?: 0
        val stored = if (timer > 0) message.copy(expiresAt = message.sentAt.plusSeconds(timer.toLong())) else message
        messages.update { map -> map + (stored.conversationId to (map[stored.conversationId].orEmpty() + stored)) }
        refreshPreview(stored.conversationId)
        if (timer > 0) {
            scope.launch {
                delay(timer * MILLIS_PER_SECOND)
                messages.update { map ->
                    map + (stored.conversationId to map[stored.conversationId].orEmpty().filterNot { it.id == stored.id })
                }
                refreshPreview(stored.conversationId)
            }
        }
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
        private const val PRESENCE_REFRESH_MILLIS = 60_000L
        private const val TYPING_MILLIS = 1400L
        private const val SEARCH_LIMIT = 100
        private const val MILLIS_PER_SECOND = 1000L
        private const val FORWARDED = "forwarded"

        val conversationOrder: Comparator<Conversation> =
            compareByDescending<Conversation> { it.pinned }
                .thenBy { if (it.pinned) it.pinRank else 0 }
                .thenByDescending { it.pinned && it.kind == ConversationKind.Saved }
                .thenByDescending { it.updatedAt }
    }
}
